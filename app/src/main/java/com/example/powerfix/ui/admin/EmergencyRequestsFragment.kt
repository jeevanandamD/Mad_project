package com.example.powerfix.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.data.EmergencyRequest
import com.example.powerfix.databinding.DialogEmergencyActionBinding
import com.example.powerfix.databinding.FragmentEmergencyRequestsBinding
import com.example.powerfix.ui.common.EmergencyRequestAdapter
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

class EmergencyRequestsFragment : Fragment(R.layout.fragment_emergency_requests) {
    private var _binding: FragmentEmergencyRequestsBinding? = null
    private val binding get() = _binding!!
    private val supabase = AppContainer.supabase
    private var channel: RealtimeChannel? = null
    private lateinit var adapter: EmergencyRequestAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EmergencyRequestAdapter { request ->
            showEmergencyActionDialog(request)
        }

        binding.requestsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.requestsRecycler.adapter = adapter
        binding.listProgress.visibility = View.VISIBLE

        loadEmergencyRequests()

        // Realtime subscription for instant updates
        val realtimeChannel = supabase.realtime.channel("public:emergency_requests")
        channel = realtimeChannel
        realtimeChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "emergency_requests"
        }.onEach {
            loadEmergencyRequests()
        }.launchIn(viewLifecycleOwner.lifecycleScope)
        viewLifecycleOwner.lifecycleScope.launch { realtimeChannel.subscribe() }
    }

    private fun loadEmergencyRequests() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val docs = supabase.postgrest.from("emergency_requests")
                    .select {
                        order("created_at", order = Order.DESCENDING)
                        limit(50)
                    }
                    .decodeList<EmergencyRequest>()

                adapter.submitList(docs)
                binding.emptyText.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                binding.emptyText.text = getString(R.string.load_failed)
                binding.emptyText.visibility = View.VISIBLE
            } finally {
                binding.listProgress.visibility = View.GONE
            }
        }
    }

    private fun showEmergencyActionDialog(request: EmergencyRequest) {
        val dialogBinding = DialogEmergencyActionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.emergencyMessageText.text = request.message
        dialogBinding.emergencyTimeText.text = "Received: ${request.createdAt?.take(19)?.replace("T", " ") ?: "N/A"}"

        val statuses = resources.getStringArray(R.array.emergency_statuses)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, statuses)
        dialogBinding.statusSpinner.adapter = spinnerAdapter

        val currentIdx = statuses.indexOfFirst { it.equals(request.status, ignoreCase = true) }
        if (currentIdx >= 0) {
            dialogBinding.statusSpinner.setSelection(currentIdx)
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.updateStatusButton.setOnClickListener {
            val selectedStatus = dialogBinding.statusSpinner.selectedItem?.toString() ?: "Open"
            dialogBinding.actionProgress.visibility = View.VISIBLE
            dialogBinding.updateStatusButton.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    supabase.postgrest.from("emergency_requests").update(
                        mapOf("status" to selectedStatus)
                    ) {
                        filter { eq("id", request.id) }
                    }
                    Toast.makeText(requireContext(), "Status updated to $selectedStatus", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadEmergencyRequests()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Update failed", Toast.LENGTH_SHORT).show()
                } finally {
                    dialogBinding.actionProgress.visibility = View.GONE
                    dialogBinding.updateStatusButton.isEnabled = true
                }
            }
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
