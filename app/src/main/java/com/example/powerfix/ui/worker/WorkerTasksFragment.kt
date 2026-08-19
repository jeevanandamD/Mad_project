package com.example.powerfix.ui.worker

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
import com.example.powerfix.data.Complaint
import com.example.powerfix.databinding.DialogWorkerTaskActionBinding
import com.example.powerfix.databinding.FragmentWorkerTasksBinding
import com.example.powerfix.ui.common.ComplaintAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkerTasksFragment : Fragment(R.layout.fragment_worker_tasks) {
    private var _binding: FragmentWorkerTasksBinding? = null
    private val binding get() = _binding!!
    private val complaintRepository get() = AppContainer.complaintRepository
    private lateinit var adapter: ComplaintAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkerTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ComplaintAdapter { complaint ->
            showWorkerTaskDialog(complaint)
        }

        binding.tasksRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.tasksRecycler.adapter = adapter
        binding.listProgress.visibility = View.VISIBLE

        loadTasks()
    }

    private fun loadTasks() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            binding.listProgress.visibility = View.GONE
            binding.emptyText.text = getString(R.string.not_signed_in)
            binding.emptyText.visibility = View.VISIBLE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // For simplicity, we filter in the flow or use a custom query
                complaintRepository.getComplaintsFlow().collectLatest { allItems ->
                    val workerTasks = allItems.filter { it.assignedWorkerId == user.uid }
                    adapter.submitList(workerTasks)
                    binding.emptyText.visibility = if (workerTasks.isEmpty()) View.VISIBLE else View.GONE
                    binding.listProgress.visibility = View.GONE
                }
            } catch (_: Exception) {
                binding.emptyText.text = getString(R.string.load_failed)
                binding.emptyText.visibility = View.VISIBLE
                binding.listProgress.visibility = View.GONE
            }
        }
    }

    private fun showWorkerTaskDialog(complaint: Complaint) {
        val dialogBinding = DialogWorkerTaskActionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.customerNameText.text = complaint.customerName.ifBlank { "Customer" }
        dialogBinding.contactInfoText.text = listOf(
            complaint.mobile.ifBlank { null },
            complaint.address.ifBlank { null }
        ).filterNotNull().joinToString(" | ")

        dialogBinding.typePriorityText.text = "${complaint.complaintType} • Priority: ${complaint.priority}"
        dialogBinding.descriptionText.text = complaint.description.ifBlank { "No description provided." }

        if (complaint.adminReply.isNotBlank()) {
            dialogBinding.adminReplyText.visibility = View.VISIBLE
            dialogBinding.adminReplyText.text = "Dispatcher Note: ${complaint.adminReply}"
        }

        val statuses = resources.getStringArray(R.array.worker_task_statuses)
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, statuses)
        dialogBinding.statusSpinner.adapter = statusAdapter

        val currentIdx = statuses.indexOfFirst { it.equals(complaint.status, ignoreCase = true) }
        if (currentIdx >= 0) {
            dialogBinding.statusSpinner.setSelection(currentIdx)
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.updateButton.setOnClickListener {
            val newStatus = dialogBinding.statusSpinner.selectedItem?.toString() ?: complaint.status
            dialogBinding.actionProgress.visibility = View.VISIBLE
            dialogBinding.updateButton.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    complaintRepository.updateComplaintStatus(complaint.id, newStatus)
                    Toast.makeText(requireContext(), "Task status updated to $newStatus", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Update failed", Toast.LENGTH_SHORT).show()
                } finally {
                    dialogBinding.actionProgress.visibility = View.GONE
                    dialogBinding.updateButton.isEnabled = true
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
