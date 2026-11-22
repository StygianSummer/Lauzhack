package tea.server.mail

/** Outbound mail (plan §10.5). */
interface Mailer {
    /** "smtp" | "console", reported by /health. */
    val kind: String

    suspend fun send(to: String, subject: String, text: String, html: String?)
}

/** Verification-code mail, localised en/fr/es (fallback en). */
object VerificationMail {
    data class Mail(val subject: String, val text: String)

    fun render(language: String, code: String): Mail = when (language.lowercase().take(2)) {
        "fr" -> Mail(
            subject = "Votre code ThisIsTea",
            text = """
                |Bonjour,
                |
                |Votre code ThisIsTea : $code
                |
                |Il est valable 10 minutes.
                |
                |Nous ne vous demanderons jamais ce code ni votre mot de passe par e-mail ou par message.
                |
                |Si vous n'avez pas demandé ce code, ignorez simplement cet e-mail.
                |
                |ThisIsTea
            """.trimMargin(),
        )
        "es" -> Mail(
            subject = "Tu código de ThisIsTea",
            text = """
                |Hola:
                |
                |Tu código de ThisIsTea: $code
                |
                |Es válido durante 10 minutos.
                |
                |Nunca te pediremos este código ni tu contraseña por correo electrónico ni por mensaje.
                |
                |Si no has pedido este código, simplemente ignora este correo.
                |
                |ThisIsTea
            """.trimMargin(),
        )
        else -> Mail(
            subject = "Your ThisIsTea code",
            text = """
                |Hello,
                |
                |Your ThisIsTea code: $code
                |
                |It is valid for 10 minutes.
                |
                |We will never ask you for this code or your password in an email or a message.
                |
                |If you did not ask for this code, just ignore this email.
                |
                |ThisIsTea
            """.trimMargin(),
        )
    }
}
