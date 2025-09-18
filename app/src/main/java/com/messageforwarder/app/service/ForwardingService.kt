package com.messageforwarder.app.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.messageforwarder.app.R
import com.messageforwarder.app.data.ConfigManager
import com.messageforwarder.app.MainActivity

class ForwardingService : Service() {
    
    companion object {
        private const val TAG = "ForwardingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "forwarding_service_channel"
        private const val CHANNEL_NAME = "消息转发服务"
        
        fun startService(context: android.content.Context) {
            val intent = Intent(context, ForwardingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: android.content.Context) {
            val intent = Intent(context, ForwardingService::class.java)
            context.stopService(intent)
        }
    }
    
    private lateinit var configManager: ConfigManager
    
    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager.getInstance(this)
        Log.d(TAG, "ForwardingService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ForwardingService started")
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 启动服务监控
        ServiceMonitor.startMonitoring(this)
        
        return START_STICKY // 服务被杀死后自动重启
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "消息转发服务正在后台运行"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("消息转发器")
            .setContentText("正在后台监控消息转发")
            .setSmallIcon(R.drawable.ic_message)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ForwardingService destroyed")
        
        // 停止服务监控
        ServiceMonitor.stopMonitoring(this)
    }
}
