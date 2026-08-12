package com.example.sucs.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.sucs.R
import com.example.sucs.databinding.FragmentComplaintsListBinding
import com.example.sucs.data.Complaint
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ComplaintsListFragment : Fragment(R.layout.fragment_complaints_list) {
    private var _binding: FragmentComplaintsListBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var registration: ListenerRegistration? = null

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
        registration = db.collection("complaints")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val items = snapshot.toObjects(Complaint::class.java)
                binding.complaintsText.text = items.joinToString("\n\n") { complaint ->
                    "${complaint.customerName} | ${complaint.status} | ${complaint.complaintType}\n${complaint.address}"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        registration?.remove()
        _binding = null
    }
}
