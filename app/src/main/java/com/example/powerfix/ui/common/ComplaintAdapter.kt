package com.example.powerfix.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.powerfix.R
import com.example.powerfix.data.Complaint
import com.example.powerfix.databinding.ItemComplaintBinding

class ComplaintAdapter(
    private val onItemClick: ((Complaint) -> Unit)? = null
) : ListAdapter<Complaint, ComplaintAdapter.ComplaintViewHolder>(ComplaintDiff) {

    inner class ComplaintViewHolder(private val binding: ItemComplaintBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Complaint) {
            binding.complaintTitle.text = item.customerName.ifBlank { item.complaintType.ifBlank { "Electricity Complaint" } }
            binding.complaintStatus.text = item.status.ifBlank { "Pending" }

            // Priority badge
            val priority = item.priority.ifBlank { "Medium" }
            binding.priorityBadge.text = priority
            val priorityColor = when (priority.lowercase()) {
                "urgent" -> ContextCompat.getColor(binding.root.context, R.color.priority_urgent)
                "high" -> ContextCompat.getColor(binding.root.context, R.color.priority_high)
                "low" -> ContextCompat.getColor(binding.root.context, R.color.priority_low)
                else -> ContextCompat.getColor(binding.root.context, R.color.priority_medium)
            }
            binding.priorityBadge.setBackgroundColor(priorityColor)

            // Status badge color
            val statusColor = when (item.status.lowercase()) {
                "pending" -> ContextCompat.getColor(binding.root.context, R.color.status_pending)
                "assigned" -> ContextCompat.getColor(binding.root.context, R.color.status_assigned)
                "in progress" -> ContextCompat.getColor(binding.root.context, R.color.status_in_progress)
                "resolved" -> ContextCompat.getColor(binding.root.context, R.color.status_resolved)
                "closed" -> ContextCompat.getColor(binding.root.context, R.color.status_closed)
                else -> ContextCompat.getColor(binding.root.context, R.color.status_pending)
            }
            binding.complaintStatus.setBackgroundColor(statusColor)

            binding.complaintMeta.text = listOf(
                item.complaintType,
                item.address,
                item.createdAt?.take(10)
            ).filter { !it.isNullOrBlank() }.joinToString(" · ")

            binding.complaintDescription.text = item.description

            // Admin reply preview
            if (item.adminReply.isNotBlank()) {
                binding.adminReplyLayout.visibility = View.VISIBLE
                binding.adminReplyPreview.text = item.adminReply
            } else {
                binding.adminReplyLayout.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComplaintViewHolder {
        val binding = ItemComplaintBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ComplaintViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComplaintViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val ComplaintDiff = object : DiffUtil.ItemCallback<Complaint>() {
            override fun areItemsTheSame(oldItem: Complaint, newItem: Complaint) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Complaint, newItem: Complaint) = oldItem == newItem
        }
    }
}
