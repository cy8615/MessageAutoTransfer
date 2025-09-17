package com.messageforwarder.app.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.messageforwarder.app.data.ConfigManager
import com.messageforwarder.app.data.HistoryManager
import com.messageforwarder.app.model.ForwardRecord
import java.text.SimpleDateFormat
import java.util.*

class NotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "NotificationListener"
        const val ACTION_NOTIFICATION_RECEIVED = "com.messageforwarder.app.NOTIFICATION_RECEIVED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TIMESTAMP = "timestamp"
    }
    
    private lateinit var configManager: ConfigManager
    private lateinit var historyManager: HistoryManager
    
    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager.getInstance(this)
        historyManager = HistoryManager.getInstance(this)
        Log.d(TAG, "NotificationListenerService created")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        // 检查服务是否启用
        if (!configManager.isServiceEnabled()) {
            return
        }
        
        try {
            val packageName = sbn.packageName
            val notification = sbn.notification
            
            // 过滤掉系统通知和本应用的通知
            if (shouldIgnoreNotification(packageName, notification)) {
                return
            }
            
            val title = getNotificationTitle(notification)
            val text = getNotificationText(notification)
            
            if (title.isNotEmpty() || text.isNotEmpty()) {
                Log.d(TAG, "Notification received - Package: $packageName, Title: $title, Text: $text")
                
                // 广播通知信息给主界面更新UI
                broadcastNotification(packageName, title, text, sbn.postTime)
                
                // 转发邮件
                forwardToEmail(packageName, title, text, sbn.postTime)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
    
    private fun shouldIgnoreNotification(packageName: String, notification: Notification): Boolean {
        // 忽略本应用的通知
        if (packageName == this.packageName) {
            return true
        }
        
        // 忽略系统UI和一些系统应用
        val systemPackages = setOf(
            "com.android.systemui",
            "android",
            "com.android.providers.downloads"
        )
        
        if (systemPackages.contains(packageName)) {
            return true
        }
        
        // 忽略正在进行的通知（如音乐播放器）
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            return true
        }
        
        // 忽略低优先级的通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (notification.priority < Notification.PRIORITY_DEFAULT) {
                return true
            }
        }
        
        return false
    }
    
    private fun getNotificationTitle(notification: Notification): String {
        val extras = notification.extras
        return extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
    }
    
    private fun getNotificationText(notification: Notification): String {
        val extras = notification.extras
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        
        // 优先使用大文本，如果没有则使用普通文本
        return if (bigText.isNotEmpty()) bigText else text
    }
    
    private fun broadcastNotification(packageName: String, title: String, text: String, timestamp: Long) {
        val intent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_TIMESTAMP, timestamp)
        }
        sendBroadcast(intent)
    }
    
    private fun forwardToEmail(packageName: String, title: String, text: String, timestamp: Long) {
        val config = configManager.getEmailConfig()
        if (!config.isValid()) {
            Log.w(TAG, "Email config is not valid, skipping forward")
            return
        }
        
        // 创建转发记录
        val record = ForwardRecord(
            id = System.currentTimeMillis().toString(),
            packageName = packageName,
            sender = getAppName(packageName),
            title = title,
            content = text,
            timestamp = timestamp,
            status = ForwardRecord.Status.PENDING,
            error = null
        )
        
        // 保存到历史记录
        historyManager.addRecord(record)
        
        // 启动邮件发送服务
        val emailIntent = Intent(this, EmailService::class.java).apply {
            putExtra(EmailService.EXTRA_RECORD_ID, record.id)
            putExtra(EmailService.EXTRA_SENDER, record.sender)
            putExtra(EmailService.EXTRA_TITLE, title)
            putExtra(EmailService.EXTRA_CONTENT, text)
            putExtra(EmailService.EXTRA_TIMESTAMP, timestamp)
        }
        
        startService(emailIntent)
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = this.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // 可以在这里处理通知被移除的逻辑
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected")
        
        // 启动前台服务确保后台运行
        ForwardingService.startService(this)
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListenerService disconnected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NotificationListenerService destroyed")
    }
}
