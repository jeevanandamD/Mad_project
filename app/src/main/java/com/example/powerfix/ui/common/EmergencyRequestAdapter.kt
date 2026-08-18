package com.example.powerfix.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.powerfix.data.EmergencyRequest
import com.example.powerfix.databinding.ItemEmergencyRequestBinding

class EmergencyRequestAdapter(
    private val onItemClick: ((EmergencyRequest) -> Unit)? = null
) : ListAdapter<EmergencyRequest, EmergencyRequestAdapter.RequestViewHolder>(RequestDiff) {

    inner class RequestViewHolder(private val binding: ItemEmergencyRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: EmergencyRequest) {
            binding.requestStatus.text = item.status.ifBlank { "Open" }
            binding.requestMessage.text = item.message.ifBlank { "No message provided" }
            binding.requestTime.text = item.createdAt?.take(19)?.replace("T", " ") ?: ""

            val statusColor = when (item.status.lowercase()) {
                "resolved" -> 0xFF2E7D32.toInt()
                "in progress" -> 0xFFF57F17.toInt()
                "dismissed" -> 0xFF546E7A.toInt()
                else -> 0xFFB02F00.toInt()
            }
            binding.requestStatus.setBackgroundColor(statusColor)

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemEmergencyRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val RequestDiff = object : DiffUtil.ItemCallback<EmergencyRequest>() {
            override fun areItemsTheSame(oldItem: EmergencyRequest, newItem: EmergencyRequest) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EmergencyRequest, newItem: EmergencyRequest) =
                oldItem == newItem
        }
    }
}
