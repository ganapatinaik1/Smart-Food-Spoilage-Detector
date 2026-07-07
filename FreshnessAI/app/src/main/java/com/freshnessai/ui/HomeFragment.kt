package com.freshnessai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.freshnessai.R
import com.freshnessai.data.ScanHistoryDatabase
import com.freshnessai.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var historyAdapter: ScanHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        
        binding.cardScan.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_scan)
        }
        
        binding.tvViewAll.setOnClickListener {
            // Setting bottom nav active item programmatically isn't ideal here, 
            // but for now just navigate to history via bottom nav
            requireActivity().findViewById<View>(R.id.historyFragment)?.performClick()
        }
        
        // Load recent scans
        val dao = ScanHistoryDatabase.getDatabase(requireContext()).scanDao()
        dao.getRecentScans(3).observe(viewLifecycleOwner) { scans ->
            if (scans.isEmpty()) {
                binding.rvRecentScans.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvRecentScans.visibility = View.VISIBLE
                historyAdapter.submitList(scans)
            }
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = ScanHistoryAdapter { record ->
            val action = HomeFragmentDirections.actionHomeToDetail(record.id)
            findNavController().navigate(action)
        }
        
        binding.rvRecentScans.apply {
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
