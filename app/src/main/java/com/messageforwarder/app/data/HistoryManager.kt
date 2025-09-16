package com.messageforwarder.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.messageforwarder.app.model.ForwardRecord

class HistoryManager private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "message_forwarder_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_SIZE = 100 // 最多保存100条记录
        
        @Volatile
        private var INSTANCE: HistoryManager? = null
        
        fun getInstance(context: Context): HistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val records = mutableListOf<ForwardRecord>()
    
    init {
        loadHistory()
    }
    
    private fun loadHistory() {
        val json = sharedPreferences.getString(KEY_HISTORY, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<ForwardRecord>>() {}.type
                val loadedRecords: List<ForwardRecord> = gson.fromJson(json, type)
                records.clear()
                records.addAll(loadedRecords)
            } catch (e: Exception) {
                // 如果解析失败，清空记录
                records.clear()
            }
        }
    }
    
    private fun saveHistory() {
        val json = gson.toJson(records)
        sharedPreferences.edit()
            .putString(KEY_HISTORY, json)
            .apply()
    }
    
    fun addRecord(record: ForwardRecord) {
        synchronized(records) {
            records.add(0, record) // 添加到列表开头
            
            // 限制历史记录数量
            if (records.size > MAX_HISTORY_SIZE) {
                records.removeAt(records.size - 1)
            }
            
            saveHistory()
        }
    }
    
    fun updateRecordStatus(recordId: String, status: ForwardRecord.Status, error: String?) {
        synchronized(records) {
            val record = records.find { it.id == recordId }
            record?.let {
                it.status = status
                it.error = error
                saveHistory()
            }
        }
    }
    
    fun getRecords(): List<ForwardRecord> {
        synchronized(records) {
            return records.toList()
        }
    }
    
    fun getRecentRecords(limit: Int = 5): List<ForwardRecord> {
        synchronized(records) {
            return records.take(limit)
        }
    }
    
    fun clearHistory() {
        synchronized(records) {
            records.clear()
            saveHistory()
        }
    }
    
    fun getRecordById(id: String): ForwardRecord? {
        synchronized(records) {
            return records.find { it.id == id }
        }
    }
    
    fun getTodayRecords(): List<ForwardRecord> {
        val today = java.util.Calendar.getInstance()
        today.set(java.util.Calendar.HOUR_OF_DAY, 0)
        today.set(java.util.Calendar.MINUTE, 0)
        today.set(java.util.Calendar.SECOND, 0)
        today.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = today.timeInMillis
        
        synchronized(records) {
            return records.filter { it.timestamp >= todayStart }
        }
    }
    
    fun getSuccessCount(): Int {
        synchronized(records) {
            return records.count { it.status == ForwardRecord.Status.SUCCESS }
        }
    }
}
