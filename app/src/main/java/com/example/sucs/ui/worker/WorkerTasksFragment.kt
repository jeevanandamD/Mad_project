package com.example.sucs.ui.worker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.sucs.R
import com.example.sucs.databinding.FragmentWorkerTasksBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class WorkerTasksFragment : Fragment(R.layout.fragment_worker_tasks) {
    private var _binding: FragmentWorkerTasksBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var registration: ListenerRegistration? = null

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
        registration = db.collection("complaints")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val items = snapshot.documents
                binding.tasksText.text = items.joinToString("\n\n") { doc ->
                    val type = doc.getString("complaintType") ?: "Unknown"
                    val status = doc.getString("status") ?: "Pending"
                    "$type - $status"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        registration?.remove()
        _binding = null
    }
}
