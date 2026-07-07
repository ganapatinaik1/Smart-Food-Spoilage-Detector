package com.freshnessai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.freshnessai.R
import com.freshnessai.data.ScanHistoryDatabase
import com.freshnessai.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: ScanHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        val dao = ScanHistoryDatabase.getDatabase(requireContext()).scanDao()

        dao.getAllScans().observe(viewLifecycleOwner) { scans ->
            if (scans.isEmpty()) {
                binding.rvHistory.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.btnClear.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                binding.btnClear.visibility = View.VISIBLE
                historyAdapter.submitList(scans)
            }
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.history_clear_all)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.yes) { _, _ ->
                    lifecycleScope.launch {
                        dao.deleteAllScans()
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = ScanHistoryAdapter { record ->
            val action = HistoryFragmentDirections.actionHistoryToDetail(record.id)
            findNavController().navigate(action)
        }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
            setHasFixedSize(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
