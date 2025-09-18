package com.messageforwarder.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.messageforwarder.app.data.ConfigManager

class ServiceMonitor : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ServiceMonitor"
        private const val ACTION_CHECK_SERVICE = "com.messageforwarder.app.CHECK_SERVICE"
        private const val REQUEST_CODE_MONITOR = 1001
        private const val CHECK_INTERVAL = 5 * 60 * 1000L // 5分钟检查一次
        
        fun startMonitoring(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceMonitor::class.java).apply {
                action = ACTION_CHECK_SERVICE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                REQUEST_CODE_MONITOR, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 使用精确的闹钟（Android 12+需要特殊权限）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + CHECK_INTERVAL,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + CHECK_INTERVAL,
                    pendingIntent
                )
            }
            
            Log.d(TAG, "Service monitoring started")
        }
        
        fun stopMonitoring(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceMonitor::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                REQUEST_CODE_MONITOR, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Service monitoring stopped")
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        Log.d(TAG, "ServiceMonitor received: ${intent.action}")
        
        when (intent.action) {
            ACTION_CHECK_SERVICE -> {
                checkAndRestartServices(context)
                // 重新设置下一次检查
                startMonitoring(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // 开机后延迟启动监控
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val configManager = ConfigManager.getInstance(context)
                    if (configManager.isServiceEnabled()) {
                        startMonitoring(context)
                        ForwardingService.startService(context)
                    }
                }, 10000) // 延迟10秒
            }
        }
    }
    
    private fun checkAndRestartServices(context: Context) {
        val configManager = ConfigManager.getInstance(context)
        
        // 检查服务是否应该运行
        if (!configManager.isServiceEnabled()) {
            Log.d(TAG, "Service is disabled, skipping check")
            return
        }
        
        // 检查通知监听器是否启用
        if (!isNotificationListenerEnabled(context)) {
            Log.w(TAG, "Notification listener is not enabled")
            return
        }
        
        // 检查前台服务是否运行
        if (!isServiceRunning(context, ForwardingService::class.java)) {
            Log.w(TAG, "ForwardingService is not running, restarting...")
            ForwardingService.startService(context)
        }
        
        // 检查通知监听服务是否运行
        if (!isServiceRunning(context, NotificationListenerService::class.java)) {
            Log.w(TAG, "NotificationListenerService is not running, this is normal")
        }
        
        Log.d(TAG, "Service check completed")
    }
    
    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = context.packageName
        return enabledListeners != null && enabledListeners.contains(packageName)
    }
    
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (serviceInfo in runningServices) {
            if (serviceClass.name == serviceInfo.service.className) {
                return true
            }
        }
        return false
    }
}
