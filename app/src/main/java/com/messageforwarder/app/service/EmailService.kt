package com.messageforwarder.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.messageforwarder.app.data.ConfigManager
import com.messageforwarder.app.data.HistoryManager
import com.messageforwarder.app.model.EmailConfig
import com.messageforwarder.app.model.ForwardRecord
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import javax.mail.*
import javax.mail.internet.*

class EmailService : Service() {
    
    companion object {
        private const val TAG = "EmailService"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_TIMESTAMP = "timestamp"
        
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 5000L
    }
    
    private lateinit var configManager: ConfigManager
    private lateinit var historyManager: HistoryManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager.getInstance(this)
        historyManager = HistoryManager.getInstance(this)
        Log.d(TAG, "EmailService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleEmailSend(it, startId) }
        return START_NOT_STICKY
    }
    
    private fun handleEmailSend(intent: Intent, startId: Int) {
        val recordId = intent.getStringExtra(EXTRA_RECORD_ID) ?: return
        val sender = intent.getStringExtra(EXTRA_SENDER) ?: "Unknown"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        
        serviceScope.launch {
            try {
                sendEmailWithRetry(recordId, sender, title, content, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send email after all retries", e)
                updateRecordStatus(recordId, ForwardRecord.Status.FAILED, e.message)
            } finally {
                stopSelf(startId)
            }
        }
    }
    
    private suspend fun sendEmailWithRetry(
        recordId: String,
        sender: String,
        title: String,
        content: String,
        timestamp: Long
    ) {
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRY_COUNT) {
            try {
                Log.d(TAG, "Sending email, attempt $attempt/$MAX_RETRY_COUNT")
                sendEmail(sender, title, content, timestamp)
                
                // 发送成功
                updateRecordStatus(recordId, ForwardRecord.Status.SUCCESS, null)
                Log.d(TAG, "Email sent successfully")
                return
                
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Email send attempt $attempt failed", e)
                
                if (attempt < MAX_RETRY_COUNT) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        // 所有重试都失败了
        throw lastException ?: Exception("Unknown error")
    }
    
    private suspend fun sendEmail(sender: String, title: String, content: String, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val config = configManager.getEmailConfig()
            if (!config.isValid()) {
                throw Exception("Email configuration is not valid")
            }
            
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.host", config.smtpServer)
                put("mail.smtp.port", config.smtpPort)
                
                when (config.encryption.lowercase()) {
                    "tls" -> {
                        put("mail.smtp.starttls.enable", "true")
                        put("mail.smtp.starttls.required", "true")
                    }
                    "ssl" -> {
                        put("mail.smtp.socketFactory.port", config.smtpPort)
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                        put("mail.smtp.socketFactory.fallback", "false")
                    }
                }
                
                // 设置超时
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
                put("mail.smtp.writetimeout", "10000")
            }
            
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.senderEmail, config.senderPassword)
                }
            })
            
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.senderEmail))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.recipientEmail))
                
                // 邮件标题使用发件人名称
                subject = "[$sender] $title"
                
                // 邮件内容
                val emailContent = buildEmailContent(sender, title, content, timestamp)
                setText(emailContent, "utf-8")
                
                sentDate = Date()
            }
            
            Transport.send(message)
        }
    }
    
    private fun buildEmailContent(sender: String, title: String, content: String, timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStr = dateFormat.format(Date(timestamp))
        
        return buildString {
            appendLine("消息转发通知")
            appendLine("=".repeat(30))
            appendLine()
            appendLine("发送应用: $sender")
            if (title.isNotEmpty()) {
                appendLine("标题: $title")
            }
            appendLine("接收时间: $timeStr")
            appendLine()
            appendLine("内容:")
            appendLine(content)
            appendLine()
            appendLine("=".repeat(30))
            appendLine("此邮件由消息转发器自动发送")
        }
    }
    
    private fun updateRecordStatus(recordId: String, status: ForwardRecord.Status, error: String?) {
        historyManager.updateRecordStatus(recordId, status, error)
        
        // 如果发送成功，更新统计
        if (status == ForwardRecord.Status.SUCCESS) {
            configManager.incrementTodayCount()
        }
        
        // 广播状态更新
        val intent = Intent("com.messageforwarder.app.EMAIL_STATUS_UPDATE").apply {
            putExtra("record_id", recordId)
            putExtra("status", status.name)
            putExtra("error", error)
        }
        sendBroadcast(intent)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "EmailService destroyed")
    }
}
