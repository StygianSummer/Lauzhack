package tea.server.mail

import org.slf4j.LoggerFactory

/** Dev only: prints mails to the log instead of sending them (used when SMTP_PASSWORD is empty). */
class ConsoleMailer : Mailer {
    private val log = LoggerFactory.getLogger(ConsoleMailer::class.java)
    override val kind = "console"

    override suspend fun send(to: String, subject: String, text: String, html: String?) {
        log.info("ConsoleMailer (dev only, nothing sent)\nTo: {}\nSubject: {}\n\n{}\n", to, subject, text)
    }
}
