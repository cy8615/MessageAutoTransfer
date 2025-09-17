package com.messageforwarder.app.model

data class EmailConfig(
    val smtpServer: String = "smtp.qq.com",
    val smtpPort: String = "465",
    val encryption: String = "ssl",
    val senderEmail: String = "",
    val senderPassword: String = "",
    val recipientEmail: String = ""
) {
    fun isValid(): Boolean {
        return smtpServer.isNotEmpty() &&
                smtpPort.isNotEmpty() &&
                senderEmail.isNotEmpty() &&
                senderPassword.isNotEmpty() &&
                recipientEmail.isNotEmpty() &&
                isValidEmail(senderEmail) &&
                isValidEmail(recipientEmail) &&
                smtpPort.toIntOrNull() != null
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
