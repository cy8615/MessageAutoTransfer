package com.messageforwarder.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.messageforwarder.app.model.EmailConfig

class ConfigManager private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "message_forwarder_config"
        private const val KEY_EMAIL_CONFIG = "email_config"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_TODAY_COUNT = "today_count"
        private const val KEY_TOTAL_COUNT = "total_count"
        private const val KEY_LAST_ACTIVE = "last_active"
        private const val KEY_LAST_DATE = "last_date"
        
        @Volatile
        private var INSTANCE: ConfigManager? = null
        
        fun getInstance(context: Context): ConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences
    private val gson = Gson()
    
    init {
        // 使用加密的SharedPreferences来存储敏感信息
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        sharedPreferences = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 如果加密失败，使用普通的SharedPreferences
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    fun saveEmailConfig(config: EmailConfig) {
        val json = gson.toJson(config)
        sharedPreferences.edit()
            .putString(KEY_EMAIL_CONFIG, json)
            .apply()
    }
    
    fun getEmailConfig(): EmailConfig {
        val json = sharedPreferences.getString(KEY_EMAIL_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, EmailConfig::class.java)
            } catch (e: Exception) {
                EmailConfig()
            }
        } else {
            EmailConfig()
        }
    }
    
    fun setServiceEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_SERVICE_ENABLED, enabled)
            .apply()
    }
    
    fun isServiceEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false)
    }
    
    fun incrementTodayCount() {
        val currentDate = getCurrentDateString()
        val lastDate = sharedPreferences.getString(KEY_LAST_DATE, "")
        
        if (currentDate != lastDate) {
            // 新的一天，重置今日计数
            sharedPreferences.edit()
                .putInt(KEY_TODAY_COUNT, 1)
                .putString(KEY_LAST_DATE, currentDate)
                .apply()
        } else {
            // 同一天，增加计数
            val currentCount = sharedPreferences.getInt(KEY_TODAY_COUNT, 0)
            sharedPreferences.edit()
                .putInt(KEY_TODAY_COUNT, currentCount + 1)
                .apply()
        }
        
        // 增加总计数
        val totalCount = sharedPreferences.getInt(KEY_TOTAL_COUNT, 0)
        sharedPreferences.edit()
            .putInt(KEY_TOTAL_COUNT, totalCount + 1)
            .apply()
    }
    
    fun getTodayCount(): Int {
        val currentDate = getCurrentDateString()
        val lastDate = sharedPreferences.getString(KEY_LAST_DATE, "")
        
        return if (currentDate == lastDate) {
            sharedPreferences.getInt(KEY_TODAY_COUNT, 0)
        } else {
            0
        }
    }
    
    fun getTotalCount(): Int {
        return sharedPreferences.getInt(KEY_TOTAL_COUNT, 0)
    }
    
    fun updateLastActive() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
            .apply()
    }
    
    fun getLastActive(): Long {
        return sharedPreferences.getLong(KEY_LAST_ACTIVE, 0)
    }
    
    private fun getCurrentDateString(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.MONTH)}-${calendar.get(java.util.Calendar.DAY_OF_MONTH)}"
    }
}
