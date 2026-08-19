package com.example.powerfix.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.data.Complaint
import com.example.powerfix.databinding.DialogCustomerComplaintDetailBinding
import com.example.powerfix.databinding.FragmentComplaintTrackingBinding
import com.example.powerfix.ui.common.ComplaintAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ComplaintTrackingFragment : Fragment(R.layout.fragment_complaint_tracking) {
    private var _binding: FragmentComplaintTrackingBinding? = null
    private val binding get() = _binding!!
    private val complaintRepository get() = AppContainer.complaintRepository
    private lateinit var adapter: ComplaintAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComplaintTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ComplaintAdapter { complaint ->
            showComplaintDetailDialog(complaint)
        }

        binding.complaintsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.complaintsRecycler.adapter = adapter
        binding.listProgress.visibility = View.VISIBLE

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            binding.listProgress.visibility = View.GONE
            binding.emptyText.text = getString(R.string.not_signed_in)
            binding.emptyText.visibility = View.VISIBLE
            return
        }

        observeCustomerComplaints(user.uid)
    }

    private fun observeCustomerComplaints(uid: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                complaintRepository.getCustomerComplaintsFlow(uid).collectLatest { items ->
                    adapter.submitList(items)
                    binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.listProgress.visibility = View.GONE
                }
            } catch (_: Exception) {
                binding.emptyText.text = getString(R.string.load_failed)
                binding.emptyText.visibility = View.VISIBLE
                binding.listProgress.visibility = View.GONE
            }
        }
    }

    private fun showComplaintDetailDialog(complaint: Complaint) {
        val dialogBinding = DialogCustomerComplaintDetailBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val status = complaint.status.ifBlank { "Pending" }
        dialogBinding.detailStatusBadge.text = status

        val statusColor = when (status.lowercase()) {
            "pending" -> ContextCompat.getColor(requireContext(), R.color.status_pending)
            "assigned" -> ContextCompat.getColor(requireContext(), R.color.status_assigned)
            "in progress" -> ContextCompat.getColor(requireContext(), R.color.status_in_progress)
            "resolved" -> ContextCompat.getColor(requireContext(), R.color.status_resolved)
            "closed" -> ContextCompat.getColor(requireContext(), R.color.status_closed)
            else -> ContextCompat.getColor(requireContext(), R.color.status_pending)
        }
        dialogBinding.detailStatusBadge.setBackgroundColor(statusColor)

        val etaMinutes = complaint.estimatedEtaMinutes()
        val etaText = if (etaMinutes > 0) " • Est. ETA: ~${etaMinutes} mins" else ""
        dialogBinding.detailTypePriorityText.text = "${complaint.complaintType} • Priority: ${complaint.priority}$etaText"
        dialogBinding.detailDateText.text = "Submitted: ${complaint.createdAt?.take(19)?.replace("T", " ") ?: "N/A"}"
        dialogBinding.detailDescriptionText.text = complaint.description.ifBlank { "No description provided." }
        dialogBinding.detailAddressText.text = complaint.address.ifBlank { "Location: N/A" }

        if (complaint.adminReply.isNotBlank()) {
            dialogBinding.adminReplyCard.visibility = View.VISIBLE
            dialogBinding.adminReplyFullText.text = complaint.adminReply
        } else {
            dialogBinding.adminReplyCard.visibility = View.VISIBLE
            dialogBinding.adminReplyFullText.text = "Your complaint is registered and assigned technicians are scheduled."
        }

        dialogBinding.closeDetailButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
