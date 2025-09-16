package com.messageforwarder.app.model

data class ForwardRecord(
    val id: String,
    val packageName: String,
    val sender: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    var status: Status,
    var error: String?
) {
    enum class Status {
        PENDING,    // 等待发送
        SUCCESS,    // 发送成功
        FAILED      // 发送失败
    }
    
    fun getDisplayTime(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        val now = java.util.Calendar.getInstance()
        val diffInMillis = now.timeInMillis - timestamp
        val diffInMinutes = diffInMillis / (1000 * 60)
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24
        
        return when {
            diffInMinutes < 1 -> "刚刚"
            diffInMinutes < 60 -> "${diffInMinutes}分钟前"
            diffInHours < 24 -> "${diffInHours}小时前"
            diffInDays < 30 -> "${diffInDays}天前"
            else -> {
                val format = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                format.format(java.util.Date(timestamp))
            }
        }
    }
    
    fun getContentPreview(): String {
        val fullContent = if (title.isNotEmpty()) "$title: $content" else content
        return if (fullContent.length > 50) {
            fullContent.substring(0, 50) + "..."
        } else {
            fullContent
        }
    }
}
