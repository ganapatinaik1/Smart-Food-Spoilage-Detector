package com.freshnessai.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.freshnessai.R
import com.freshnessai.data.FreshnessStatus
import com.freshnessai.data.ScanRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanHistoryAdapter(
    private val onClick: (ScanRecord) -> Unit
) : ListAdapter<ScanRecord, ScanHistoryAdapter.ViewHolder>(ScanDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_history, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), dateFormat)
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (ScanRecord) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvProductName: TextView = itemView.findViewById(R.id.tv_product_name)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val tvScore: TextView = itemView.findViewById(R.id.tv_score)
        private val colorStrip: View = itemView.findViewById(R.id.color_strip)
        private val ivCategory: ImageView = itemView.findViewById(R.id.iv_category)
        
        fun bind(record: ScanRecord, dateFormat: SimpleDateFormat) {
            itemView.setOnClickListener { onClick(record) }
            
            tvProductName.text = record.productName
            tvDate.text = dateFormat.format(Date(record.scanTimestamp))
            tvScore.text = record.freshnessScore.toString()
            
            tvStatus.text = record.statusLabel.uppercase()
            
            // Set status color
            val statusColorRes = when(record.status) {
                FreshnessStatus.EXCELLENT.name -> R.color.freshness_excellent
                FreshnessStatus.GOOD.name -> R.color.freshness_good
                FreshnessStatus.FAIR.name -> R.color.freshness_fair
                FreshnessStatus.CAUTION.name -> R.color.freshness_caution
                FreshnessStatus.SPOILED.name -> R.color.freshness_spoiled
                FreshnessStatus.DANGEROUS.name -> R.color.freshness_dangerous
                else -> R.color.primary
            }
            
            val statusColor = ContextCompat.getColor(itemView.context, statusColorRes)
            tvStatus.backgroundTintList = ColorStateList.valueOf(statusColor)
            tvScore.setTextColor(statusColor)
            
            // Set derived color strip
            colorStrip.setBackgroundColor(record.detectedColorInt)
            
            // Set category icon based on category type
            val iconRes = when(record.category.lowercase()) {
                "dairy" -> R.drawable.ic_category // using generic person for now, can replace later
                else -> R.drawable.ic_category
            }
            ivCategory.setImageResource(iconRes)
        }
    }

    class ScanDiffCallback : DiffUtil.ItemCallback<ScanRecord>() {
        override fun areItemsTheSame(oldItem: ScanRecord, newItem: ScanRecord): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ScanRecord, newItem: ScanRecord): Boolean =
            oldItem == newItem
    }
}
