package com.messageforwarder.app

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.messageforwarder.app.adapter.HistoryAdapter
import com.messageforwarder.app.data.ConfigManager
import com.messageforwarder.app.data.HistoryManager
import com.messageforwarder.app.model.EmailConfig
import com.messageforwarder.app.model.ForwardRecord
import com.messageforwarder.app.service.NotificationListenerService
import com.messageforwarder.app.service.ForwardingService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        private const val REQUEST_POST_NOTIFICATIONS = 1002
        private const val REQUEST_BATTERY_OPTIMIZATION = 1003
    }
    
    private lateinit var configManager: ConfigManager
    private lateinit var historyManager: HistoryManager
    private lateinit var historyAdapter: HistoryAdapter
    private var currentPage = 0
    private var allRecords = mutableListOf<ForwardRecord>()
    
    // UI组件
    private lateinit var switchService: Switch
    private lateinit var statusIndicator: View
    private lateinit var tvStatus: TextView
    private lateinit var tvLastActive: TextView
    private lateinit var tvTodayCount: TextView
    private lateinit var tvTotalCount: TextView
    
    // 配置组件
    private lateinit var etSmtpServer: TextInputEditText
    private lateinit var etSmtpPort: TextInputEditText
    private lateinit var spinnerEncryption: AutoCompleteTextView
    private lateinit var etSenderEmail: TextInputEditText
    private lateinit var etSenderPassword: TextInputEditText
    private lateinit var etRecipientEmail: TextInputEditText
    private lateinit var btnSaveConfig: MaterialButton
    
    // 历史记录组件
    private lateinit var rvHistory: RecyclerView
    private lateinit var layoutEmptyHistory: LinearLayout
    private lateinit var tvClearHistory: TextView
    
    // 广播接收器
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NotificationListenerService.ACTION_NOTIFICATION_RECEIVED -> {
                    updateStatistics()
                    updateLastActive()
                    loadHistory()
                }
                "com.messageforwarder.app.EMAIL_STATUS_UPDATE" -> {
                    loadHistory()
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initManagers()
        initViews()
        setupEncryptionSpinner()
        setupHistoryRecyclerView()
        setupClickListeners()
        loadConfiguration()
        updateUI()
        checkPermissions()
        
        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction(NotificationListenerService.ACTION_NOTIFICATION_RECEIVED)
            addAction("com.messageforwarder.app.EMAIL_STATUS_UPDATE")
        }
        registerReceiver(notificationReceiver, filter)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
    }
    
    private fun initManagers() {
        configManager = ConfigManager.getInstance(this)
        historyManager = HistoryManager.getInstance(this)
    }
    
    private fun initViews() {
        // 状态组件
        switchService = findViewById(R.id.switch_service)
        statusIndicator = findViewById(R.id.status_indicator)
        tvStatus = findViewById(R.id.tv_status)
        tvLastActive = findViewById(R.id.tv_last_active)
        tvTodayCount = findViewById(R.id.tv_today_count)
        tvTotalCount = findViewById(R.id.tv_total_count)
        
        // 配置组件
        etSmtpServer = findViewById(R.id.et_smtp_server)
        etSmtpPort = findViewById(R.id.et_smtp_port)
        spinnerEncryption = findViewById(R.id.spinner_encryption)
        etSenderEmail = findViewById(R.id.et_sender_email)
        etSenderPassword = findViewById(R.id.et_sender_password)
        etRecipientEmail = findViewById(R.id.et_recipient_email)
        btnSaveConfig = findViewById(R.id.btn_save_config)
        
        // 历史记录组件
        rvHistory = findViewById(R.id.rv_history)
        layoutEmptyHistory = findViewById(R.id.layout_empty_history)
        tvClearHistory = findViewById(R.id.tv_clear_history)
        
        // 帮助和设置按钮
        findViewById<ImageButton>(R.id.btn_help).setOnClickListener { showHelpDialog() }
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener { openNotificationSettings() }
    }
    
    private fun setupEncryptionSpinner() {
        val encryptionOptions = arrayOf("SSL", "TLS", "无")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, encryptionOptions)
        spinnerEncryption.setAdapter(adapter)
        spinnerEncryption.setText("SSL", false)
        
        // 设置加密方式变化监听器
        spinnerEncryption.setOnItemClickListener { _, _, position, _ ->
            updatePortBasedOnEncryption(position)
        }
        
        // 设置初始端口
        updatePortBasedOnEncryption(0)
    }
    
    private fun updatePortBasedOnEncryption(position: Int) {
        when (position) {
            0 -> etSmtpPort.setText("465") // SSL
            1 -> etSmtpPort.setText("587") // TLS
            2 -> etSmtpPort.setText("25")  // 无加密
        }
    }
    
    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClick = { record ->
                showRecordDetails(record)
            },
            onLoadMore = {
                loadMoreHistory()
            }
        )
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
    }
    
    private fun setupClickListeners() {
        switchService.setOnCheckedChangeListener { _, isChecked ->
            handleServiceToggle(isChecked)
        }
        
        btnSaveConfig.setOnClickListener {
            saveConfiguration()
        }
        
        tvClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }
    }
    
    private fun handleServiceToggle(enabled: Boolean) {
        if (enabled) {
            if (!isNotificationServiceEnabled()) {
                showToast("请先开启通知访问权限")
                switchService.isChecked = false
                openNotificationSettings()
                return
            }
            
            val config = getCurrentEmailConfig()
            if (!config.isValid()) {
                showToast("请先填写完整的邮箱配置")
                switchService.isChecked = false
                return
            }
            
            configManager.setServiceEnabled(true)
            configManager.updateLastActive()
            
            // 启动前台服务确保锁屏后仍能运行
            ForwardingService.startService(this)
            
            showToast("转发服务已启动")
        } else {
            configManager.setServiceEnabled(false)
            
            // 停止前台服务
            ForwardingService.stopService(this)
            
            showToast("转发服务已停止")
        }
        
        updateServiceStatus()
    }
    
    private fun saveConfiguration() {
        val config = getCurrentEmailConfig()
        
        if (!config.isValid()) {
            showToast("请检查邮箱配置信息")
            return
        }
        
        configManager.saveEmailConfig(config)
        showToast("配置保存成功")
        
        // 如果服务已启用，重新检查配置
        if (configManager.isServiceEnabled()) {
            updateServiceStatus()
        }
    }
    
    private fun getCurrentEmailConfig(): EmailConfig {
        return EmailConfig(
            smtpServer = etSmtpServer.text.toString().trim(),
            smtpPort = etSmtpPort.text.toString().trim(),
            encryption = when (spinnerEncryption.text.toString()) {
                "TLS" -> "tls"
                "无" -> "none"
                else -> "ssl"
            },
            senderEmail = etSenderEmail.text.toString().trim(),
            senderPassword = etSenderPassword.text.toString().trim(),
            recipientEmail = etRecipientEmail.text.toString().trim()
        )
    }
    
    private fun loadConfiguration() {
        val config = configManager.getEmailConfig()
        etSmtpServer.setText(config.smtpServer)
        etSmtpPort.setText(config.smtpPort)
        val encryptionText = when (config.encryption) {
            "tls" -> "TLS"
            "none" -> "无"
            else -> "SSL"
        }
        spinnerEncryption.setText(encryptionText, false)
        etSenderEmail.setText(config.senderEmail)
        etSenderPassword.setText(config.senderPassword)
        etRecipientEmail.setText(config.recipientEmail)
        
        switchService.isChecked = configManager.isServiceEnabled()
    }
    
    private fun updateUI() {
        updateServiceStatus()
        updateStatistics()
        updateLastActive()
        loadHistory()
    }
    
    private fun updateServiceStatus() {
        val isEnabled = configManager.isServiceEnabled()
        val isListenerEnabled = isNotificationServiceEnabled()
        val config = configManager.getEmailConfig()
        
        if (isEnabled && isListenerEnabled && config.isValid()) {
            statusIndicator.setBackgroundResource(R.drawable.circle_green)
            tvStatus.text = "服务运行中"
        } else {
            statusIndicator.setBackgroundResource(R.drawable.circle_red)
            tvStatus.text = "服务未运行"
        }
    }
    
    private fun updateStatistics() {
        tvTodayCount.text = configManager.getTodayCount().toString()
        tvTotalCount.text = configManager.getTotalCount().toString()
    }
    
    private fun updateLastActive() {
        val lastActive = configManager.getLastActive()
        if (lastActive > 0) {
            val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            tvLastActive.text = "上次活动: ${format.format(Date(lastActive))}"
        } else {
            tvLastActive.text = "上次活动: 从未"
        }
    }
    
    private fun loadHistory() {
        currentPage = 0
        allRecords.clear()
        val records = historyManager.getRecordsByPage(currentPage)
        allRecords.addAll(records)
        historyAdapter.submitList(allRecords.toList())
        historyAdapter.setLoading(false)
        
        if (allRecords.isEmpty()) {
            layoutEmptyHistory.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
        } else {
            layoutEmptyHistory.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE
        }
    }
    
    private fun loadMoreHistory() {
        if (historyManager.hasMorePages(currentPage)) {
            currentPage++
            val newRecords = historyManager.getRecordsByPage(currentPage)
            allRecords.addAll(newRecords)
            historyAdapter.submitList(allRecords.toList())
        }
        historyAdapter.setLoading(false)
    }
    
    private fun showRecordDetails(record: ForwardRecord) {
        val message = buildString {
            appendLine("发送人: ${record.sender}")
            if (record.title.isNotEmpty()) {
                appendLine("标题: ${record.title}")
            }
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))}")
            appendLine("状态: ${when (record.status) {
                ForwardRecord.Status.SUCCESS -> "发送成功"
                ForwardRecord.Status.FAILED -> "发送失败"
                ForwardRecord.Status.PENDING -> "发送中"
            }}")
            if (record.error != null) {
                appendLine("错误: ${record.error}")
            }
            appendLine()
            appendLine("内容:")
            append(record.content)
        }
        
        AlertDialog.Builder(this)
            .setTitle("消息详情")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空历史")
            .setMessage("确定要清空所有转发历史吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                historyManager.clearHistory()
                loadHistory()
                showToast("历史记录已清空")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showHelpDialog() {
        val helpText = """
            使用帮助：
            
            1. 配置邮箱信息
            - SMTP服务器：如 smtp.qq.com
            - 端口：通常为 587 (TLS) 或 465 (SSL)
            - 发件人邮箱：用于发送转发邮件的邮箱
            - 密码/授权码：邮箱密码或第三方授权码
            - 收件人邮箱：接收转发邮件的邮箱
            
            2. 开启通知访问权限
            - 点击设置按钮进入系统设置
            - 找到并开启"消息转发器"的通知访问权限
            
            3. 启动转发服务
            - 完成配置后，开启顶部的转发开关
            - 所有通知消息将自动转发至指定邮箱
            
            注意事项：
            - QQ邮箱需要使用授权码而非登录密码
            - 确保网络连接正常
            - 转发历史仅保存在本地设备
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("使用帮助")
            .setMessage(helpText)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun checkPermissions() {
        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 
                    REQUEST_POST_NOTIFICATIONS
                )
            }
        }
        
        // 检查通知监听权限
        if (!isNotificationServiceEnabled()) {
            showNotificationPermissionDialog()
        }
        
        // 检查电池优化白名单
        checkBatteryOptimization()
    }
    
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog()
            }
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("电池优化设置")
            .setMessage("为了确保消息转发服务在锁屏后仍能正常运行，建议将应用加入电池优化白名单。")
            .setPositiveButton("去设置") { _, _ ->
                requestBatteryOptimizationWhitelist()
            }
            .setNegativeButton("稍后", null)
            .show()
    }
    
    private fun requestBatteryOptimizationWhitelist() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_BATTERY_OPTIMIZATION)
            } catch (e: Exception) {
                // 如果无法打开设置页面，引导用户手动设置
                showToast("请手动在系统设置中将应用加入电池优化白名单")
            }
        }
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = packageName
        return enabledListeners != null && enabledListeners.contains(packageName)
    }
    
    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要通知访问权限")
            .setMessage("为了监听并转发通知消息，请授予通知访问权限。")
            .setPositiveButton("去设置") { _, _ ->
                openNotificationSettings()
            }
            .setNegativeButton("稍后", null)
            .show()
    }
    
    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            showToast("无法打开设置页面")
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showToast("通知权限已授予")
                } else {
                    showToast("需要通知权限以正常工作")
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
