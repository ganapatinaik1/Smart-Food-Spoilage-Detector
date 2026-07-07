package com.freshnessai.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.freshnessai.R
import com.freshnessai.data.FreshnessStatus
import com.freshnessai.data.ScanHistoryDatabase
import com.freshnessai.data.ScanRecord
import com.freshnessai.databinding.FragmentResultBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    
    private val args: ResultFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnDone.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        
        binding.btnScanAgain.setOnClickListener {
            findNavController().navigateUp()
        }
        
        loadRecord()
    }

    private fun loadRecord() {
        val dao = ScanHistoryDatabase.getDatabase(requireContext()).scanDao()
        lifecycleScope.launch {
            val record = dao.getScanById(args.scanRecordId)
            if (record != null) {
                populateUI(record)
            }
        }
    }

    private fun populateUI(record: ScanRecord) {
        val statusEnum = try { FreshnessStatus.valueOf(record.status) } catch(e: Exception) { FreshnessStatus.FAIR }
        val statusColorRes = when(statusEnum) {
            FreshnessStatus.EXCELLENT -> R.color.freshness_excellent
            FreshnessStatus.GOOD -> R.color.freshness_good
            FreshnessStatus.FAIR -> R.color.freshness_fair
            FreshnessStatus.CAUTION -> R.color.freshness_caution
            FreshnessStatus.SPOILED -> R.color.freshness_spoiled
            FreshnessStatus.DANGEROUS -> R.color.freshness_dangerous
        }
        
        val statusColor = ContextCompat.getColor(requireContext(), statusColorRes)
        
        // Gauge
        binding.gaugeView.setScore(record.freshnessScore, statusColorRes)
        
        // Status Card
        binding.tvStatusTitle.text = "${statusEnum.emoji} ${record.statusLabel}"
        binding.tvStatusTitle.setTextColor(statusColor)
        binding.tvStatusDesc.text = record.statusDescription
        
        if (record.edible) {
            binding.tvEdibleStatus.text = "YES"
            binding.tvEdibleStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.freshness_excellent))
        } else {
            binding.tvEdibleStatus.text = "NO"
            binding.tvEdibleStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.freshness_dangerous))
        }
        
        if (record.remainingHours > 48) {
            binding.tvTimeLeft.text = getString(R.string.result_days_left, record.remainingHours / 24)
        } else {
            binding.tvTimeLeft.text = getString(R.string.result_hours_left, record.remainingHours)
        }
        
        // Sensor
        binding.tvPhVal.text = String.format("%.1f", record.estimatedPh)
        binding.viewDetectedColor.backgroundTintList = ColorStateList.valueOf(record.detectedColorInt)
        binding.tvColorName.text = record.colorLabel
        
        // Details
        binding.rowProduct.tvLabel.text = getString(R.string.result_product)
        binding.rowProduct.tvValue.text = record.productName
        
        binding.rowCategory.tvLabel.text = getString(R.string.result_category)
        binding.rowCategory.tvValue.text = record.category.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        
        binding.rowMfg.tvLabel.text = getString(R.string.result_manufactured)
        binding.rowMfg.tvValue.text = record.manufacturingDate
        
        binding.rowExp.tvLabel.text = getString(R.string.result_expires)
        binding.rowExp.tvValue.text = record.expiryDate
        
        binding.rowBatch.tvLabel.text = getString(R.string.result_batch)
        binding.rowBatch.tvValue.text = record.batch
        
        // Recommendations
        val listType = object : TypeToken<List<String>>() {}.type
        val recs: List<String> = try { Gson().fromJson(record.recommendationsJson, listType) ?: emptyList() } catch(e:Exception) { emptyList() }
        
        binding.recommendationsContainer.removeAllViews()
        for (rec in recs) {
            val recView = layoutInflater.inflate(R.layout.item_recommendation, binding.recommendationsContainer, false)
            recView.findViewById<TextView>(R.id.tv_recommendation).text = rec
            binding.recommendationsContainer.addView(recView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
