package tea.server.mail

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tea.server.Config

/** SMTP over STARTTLS (Angus Mail), from `SMTP_FROM_NAME <SMTP_USER>`. */
class SmtpMailer(private val config: Config) : Mailer {
    override val kind = "smtp"

    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        }
        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(config.smtpUser, config.smtpPassword)
        })
    }

    override suspend fun send(to: String, subject: String, text: String, html: String?) = withContext(Dispatchers.IO) {
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(config.smtpUser, config.smtpFromName, "UTF-8"))
        msg.setRecipient(Message.RecipientType.TO, InternetAddress(to))
        msg.setSubject(subject, "UTF-8")
        if (html == null) {
            msg.setText(text, "UTF-8")
        } else {
            val alt = MimeMultipart("alternative")
            alt.addBodyPart(MimeBodyPart().apply { setText(text, "UTF-8") })
            alt.addBodyPart(MimeBodyPart().apply { setContent(html, "text/html; charset=UTF-8") })
            msg.setContent(alt)
        }
        Transport.send(msg)
    }
}
