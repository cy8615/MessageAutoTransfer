package com.messageforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.messageforwarder.app.data.ConfigManager
import com.messageforwarder.app.service.ForwardingService

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val configManager = ConfigManager.getInstance(context)
                
                // 如果服务之前是启用状态，开机后自动恢复
                if (configManager.isServiceEnabled()) {
                    Log.d(TAG, "Service was enabled, restoring state")
                    // 启动前台服务确保后台运行
                    ForwardingService.startService(context)
                }
            }
        }
    }
}
