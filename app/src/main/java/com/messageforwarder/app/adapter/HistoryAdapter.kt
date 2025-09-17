package com.messageforwarder.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.messageforwarder.app.R
import com.messageforwarder.app.model.ForwardRecord

class HistoryAdapter(
    private val onItemClick: (ForwardRecord) -> Unit,
    private val onLoadMore: () -> Unit = {}
) : ListAdapter<ForwardRecord, HistoryAdapter.ViewHolder>(DiffCallback()) {
    
    private var isLoading = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        
        // 当滚动到倒数第3个item时，触发加载更多
        if (position >= itemCount - 3 && !isLoading) {
            isLoading = true
            onLoadMore()
        }
    }
    
    fun setLoading(loading: Boolean) {
        isLoading = loading
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tv_sender)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val tvDetails: TextView = itemView.findViewById(R.id.tv_details)
        private val statusIndicator: View = itemView.findViewById(R.id.status_indicator)

        fun bind(record: ForwardRecord) {
            tvSender.text = record.sender
            tvTime.text = record.getDisplayTime()
            tvContent.text = record.getContentPreview()
            
            when (record.status) {
                ForwardRecord.Status.SUCCESS -> {
                    tvStatus.text = "转发成功"
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    statusIndicator.setBackgroundResource(R.drawable.circle_green)
                }
                ForwardRecord.Status.FAILED -> {
                    tvStatus.text = "转发失败"
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                    statusIndicator.setBackgroundResource(R.drawable.circle_red)
                }
                ForwardRecord.Status.PENDING -> {
                    tvStatus.text = "发送中..."
                    tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                    statusIndicator.setBackgroundResource(R.drawable.circle_primary)
                }
            }
            
            tvDetails.setOnClickListener { onItemClick(record) }
            itemView.setOnClickListener { onItemClick(record) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ForwardRecord>() {
        override fun areItemsTheSame(oldItem: ForwardRecord, newItem: ForwardRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ForwardRecord, newItem: ForwardRecord): Boolean {
            return oldItem == newItem
        }
    }
}
