package com.example.powerfix.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.data.Complaint
import com.example.powerfix.data.UserProfile
import com.example.powerfix.databinding.DialogAdminComplaintActionBinding
import com.example.powerfix.databinding.FragmentComplaintsListBinding
import com.example.powerfix.ui.common.ComplaintAdapter
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant

class ComplaintsListFragment : Fragment(R.layout.fragment_complaints_list) {
    private var _binding: FragmentComplaintsListBinding? = null
    private val binding get() = _binding!!
    private val supabase = AppContainer.supabase
    private var channel: RealtimeChannel? = null
    private lateinit var adapter: ComplaintAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComplaintsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ComplaintAdapter { complaint ->
            showAdminComplaintDialog(complaint)
        }

        binding.complaintsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.complaintsRecycler.adapter = adapter
        binding.listProgress.visibility = View.VISIBLE

        loadComplaints()

        // Live updates: reload when a complaint row changes.
        val realtimeChannel = supabase.realtime.channel("public:complaints")
        channel = realtimeChannel
        realtimeChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "complaints"
        }.onEach {
            loadComplaints()
        }.launchIn(viewLifecycleOwner.lifecycleScope)
        viewLifecycleOwner.lifecycleScope.launch { realtimeChannel.subscribe() }
    }

    private fun loadComplaints() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = supabase.postgrest.from("complaints")
                    .select {
                        order("created_at", order = Order.DESCENDING)
                        limit(100)
                    }
                    .decodeList<Complaint>()

                adapter.submitList(items)
                binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                binding.emptyText.text = getString(R.string.load_failed)
                binding.emptyText.visibility = View.VISIBLE
            } finally {
                binding.listProgress.visibility = View.GONE
            }
        }
    }

    private fun showAdminComplaintDialog(complaint: Complaint) {
        val dialogBinding = DialogAdminComplaintActionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // Bind complaint details
        dialogBinding.customerNameText.text = complaint.customerName.ifBlank { "Customer ID: ${complaint.customerId}" }
        dialogBinding.contactInfoText.text = listOf(
            complaint.mobile.ifBlank { null },
            complaint.address.ifBlank { null }
        ).filterNotNull().joinToString(" | ")

        dialogBinding.typePriorityText.text = "${complaint.complaintType} • Priority: ${complaint.priority}"
        dialogBinding.descriptionText.text = complaint.description.ifBlank { "No description provided." }
        dialogBinding.adminReplyInput.setText(complaint.adminReply)

        // Status Spinner Setup
        val statuses = resources.getStringArray(R.array.complaint_statuses)
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, statuses)
        dialogBinding.statusSpinner.adapter = statusAdapter
        val currentStatusIdx = statuses.indexOfFirst { it.equals(complaint.status, ignoreCase = true) }
        if (currentStatusIdx >= 0) {
            dialogBinding.statusSpinner.setSelection(currentStatusIdx)
        }

        // Fetch workers list from profiles
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val workers = supabase.postgrest.from("profiles")
                    .select {
                        filter { eq("role", "worker") }
                    }
                    .decodeList<UserProfile>()

                val workerDisplayList = mutableListOf("-- Unassigned --")
                val workerUidList = mutableListOf<String?>(null)

                workers.forEach { worker ->
                    val statusText = if (worker.available) "Available" else "Busy"
                    val label = "${worker.name.ifBlank { worker.email }} ($statusText)"
                    workerDisplayList.add(label)
                    workerUidList.add(worker.uid)
                }

                val workerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, workerDisplayList)
                dialogBinding.workerSpinner.adapter = workerAdapter

                // Pre-select current assigned worker
                val assignedIdx = workerUidList.indexOf(complaint.assignedWorkerId)
                if (assignedIdx >= 0) {
                    dialogBinding.workerSpinner.setSelection(assignedIdx)
                }

                // If admin chooses a worker and status was Pending, auto-suggest Assigned
                dialogBinding.workerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedWorkerUid = workerUidList[position]
                        val currentSelectedStatus = dialogBinding.statusSpinner.selectedItem?.toString()
                        if (selectedWorkerUid != null && currentSelectedStatus == "Pending") {
                            val assignedPos = statuses.indexOf("Assigned")
                            if (assignedPos >= 0) {
                                dialogBinding.statusSpinner.setSelection(assignedPos)
                            }
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }

                dialogBinding.saveButton.setOnClickListener {
                    val selectedPosition = dialogBinding.workerSpinner.selectedItemPosition
                    val selectedWorkerId = if (selectedPosition in workerUidList.indices) workerUidList[selectedPosition] else null
                    val selectedStatus = dialogBinding.statusSpinner.selectedItem?.toString() ?: complaint.status
                    val adminReplyText = dialogBinding.adminReplyInput.text?.toString()?.trim().orEmpty()

                    dialogBinding.actionProgress.visibility = View.VISIBLE
                    dialogBinding.saveButton.isEnabled = false

                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val updateData = mutableMapOf<String, Any?>(
                                "status" to selectedStatus,
                                "admin_reply" to adminReplyText,
                                "updated_at" to Instant.now().toString()
                            )
                            if (selectedWorkerId != null) {
                                updateData["assigned_worker_id"] = selectedWorkerId
                            } else {
                                updateData["assigned_worker_id"] = null
                            }

                            supabase.postgrest.from("complaints").update(updateData) {
                                filter { eq("id", complaint.id) }
                            }

                            Toast.makeText(requireContext(), "PowerFix ticket updated successfully", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadComplaints()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), e.localizedMessage ?: "Update failed", Toast.LENGTH_SHORT).show()
                        } finally {
                            dialogBinding.actionProgress.visibility = View.GONE
                            dialogBinding.saveButton.isEnabled = true
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not load workers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        val c = channel
        if (c != null) {
            viewLifecycleOwner.lifecycleScope.launch { c.unsubscribe() }
        }
        channel = null
        super.onDestroyView()
        _binding = null
    }
}
