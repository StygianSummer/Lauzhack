package tea.server.me

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import tea.model.AboutEntry
import tea.model.Profile
import tea.model.TeaJson
import tea.server.auth.requireUser
import tea.server.db.Attempts
import tea.server.db.ClientSignals
import tea.server.db.EmailCodes
import tea.server.db.Events
import tea.server.db.FieldSends
import tea.server.db.GroupMembers
import tea.server.db.Mastery
import tea.server.db.PasswordFindings
import tea.server.db.Profiles
import tea.server.db.Sessions
import tea.server.db.Users
import tea.server.field.practiceEmailsStatus
import tea.server.services

/** One string in en / fr / es. */
private class L(val en: String, val fr: String, val es: String) {
    fun of(lang: String) = when (lang) { "fr" -> fr; "es" -> es; else -> en }
}

/** Field label + purpose for each line of "What we know about you". */
private val F = mapOf(
    "email" to (L("Email address", "Adresse e-mail", "Correo electrónico") to
        L("To sign you in, send your codes and, only if you ask, one practice email.",
            "Pour vous connecter, envoyer vos codes et, seulement si vous le demandez, un e-mail d'entraînement.",
            "Para iniciar tu sesión, enviarte los códigos y, solo si lo pides, un correo de práctica.")),
    "verified" to (L("Email confirmed", "E-mail confirmé", "Correo confirmado") to
        L("To know the address really is yours.", "Pour savoir que l'adresse est bien la vôtre.", "Para saber que la dirección es realmente tuya.")),
    "created" to (L("Account created", "Compte créé", "Cuenta creada") to
        L("To keep your account records.", "Pour tenir le registre de votre compte.", "Para llevar el registro de tu cuenta.")),
    "first_name" to (L("First name", "Prénom", "Nombre") to
        L("To greet you and personalise the examples.", "Pour vous saluer et personnaliser les exemples.", "Para saludarte y personalizar los ejemplos.")),
    "last_name" to (L("Last name", "Nom", "Apellido") to
        L("Only for the initial in your leaderboard name.", "Seulement pour l'initiale de votre nom au classement.", "Solo para la inicial de tu nombre en la clasificación.")),
    "display_name" to (L("Leaderboard name", "Nom au classement", "Nombre en la clasificación") to
        L("Shown to other learners on the leaderboard.", "Affiché aux autres personnes dans le classement.", "Se muestra a otras personas en la clasificación.")),
    "language" to (L("Language", "Langue", "Idioma") to
        L("To show the app and our emails in your language.", "Pour afficher l'application et nos e-mails dans votre langue.", "Para mostrar la aplicación y nuestros correos en tu idioma.")),
    "country" to (L("Country", "Pays", "País") to
        L("To suggest help resources that exist where you live.", "Pour proposer des ressources d'aide qui existent là où vous vivez.", "Para sugerir recursos de ayuda que existen donde vives.")),
    "password" to (L("Password", "Mot de passe", "Contraseña") to
        L("To sign you in.", "Pour vous connecter.", "Para iniciar tu sesión.")),
    "terms" to (L("Terms of use", "Conditions d'utilisation", "Condiciones de uso") to
        L("To record that you agreed, and to which version.", "Pour garder la trace de votre accord, et de quelle version.", "Para registrar que aceptaste, y qué versión.")),
    "practice_emails" to (L("Practice emails", "E-mails d'entraînement", "Correos de práctica") to
        L("To send a practice email only after you ask for one, and to respect pause or stop.",
            "Pour n'envoyer un e-mail d'entraînement qu'après votre demande, et respecter pause ou arrêt.",
            "Para enviar un correo de práctica solo cuando lo pides, y respetar pausa o parada.")),
    "email_provider" to (L("Email provider", "Fournisseur d'e-mail", "Proveedor de correo") to
        L("Worked out from your address, to give tips that fit your mailbox.", "Déduit de votre adresse, pour des conseils adaptés à votre messagerie.", "Deducido de tu dirección, para dar consejos que encajen con tu buzón.")),
    "here_for" to (L("Here for", "Vous êtes là pour", "Estás aquí por") to
        L("To choose examples that fit your situation.", "Pour choisir des exemples adaptés à votre situation.", "Para elegir ejemplos que encajen con tu situación.")),
    "age_range" to (L("Age range", "Tranche d'âge", "Rango de edad") to
        L("To adapt the examples (optional).", "Pour adapter les exemples (facultatif).", "Para adaptar los ejemplos (opcional).")),
    "channels" to (L("Channels you use", "Canaux utilisés", "Canales que usas") to
        L("To focus on where you actually get messages.", "Pour se concentrer sur là où vous recevez vraiment des messages.", "Para centrarnos en donde realmente recibes mensajes.")),
    "email_app" to (L("Email app", "Application e-mail", "Aplicación de correo") to
        L("To show how to check senders and links in your app.", "Pour montrer comment vérifier expéditeurs et liens dans votre application.", "Para mostrar cómo comprobar remitentes y enlaces en tu aplicación.")),
    "interests" to (L("What you do online", "Ce que vous faites en ligne", "Lo que haces en línea") to
        L("To pick scenarios that match your life.", "Pour choisir des scénarios proches de votre vie.", "Para elegir escenarios parecidos a tu vida.")),
    "past_experience" to (L("Past experience with scams", "Expérience passée avec des arnaques", "Experiencia previa con estafas") to
        L("To use a gentler tone in feedback if you need it.", "Pour adoucir le ton des retours si besoin.", "Para usar un tono más suave en las respuestas si lo necesitas.")),
    "self_rating" to (L("How good you think you are", "Votre auto-évaluation", "Tu autoevaluación") to
        L("To compare with the placement test and help you judge your own confidence.", "Pour comparer avec le test de niveau et vous aider à juger votre confiance.", "Para comparar con la prueba de nivel y ayudarte a juzgar tu confianza.")),
    "large_text" to (L("Large text", "Grand texte", "Texto grande") to
        L("To show the app the way you like it.", "Pour afficher l'application comme vous l'aimez.", "Para mostrar la aplicación como te gusta.")),
    "dark_mode" to (L("Dark mode", "Mode sombre", "Modo oscuro") to
        L("To show the app the way you like it.", "Pour afficher l'application comme vous l'aimez.", "Para mostrar la aplicación como te gusta.")),
    "daily_goal" to (L("Daily goal", "Objectif quotidien", "Meta diaria") to
        L("To size your practice sessions.", "Pour dimensionner vos séances.", "Para ajustar la duración de tus sesiones.")),
    "placement" to (L("Placement result", "Résultat du test de niveau", "Resultado de la prueba de nivel") to
        L("To start you at the right level.", "Pour commencer au bon niveau.", "Para empezar en el nivel adecuado.")),
    "status_rating" to (L("Your status rating in the lesson", "Votre auto-évaluation dans la leçon", "Tu valoración en la lección") to
        L("To follow how you see your own progress.", "Pour suivre comment vous voyez vos progrès.", "Para seguir cómo ves tu propio progreso.")),
    "pw_shape" to (L("Your password: length and kinds of characters", "Votre mot de passe : longueur et types de caractères", "Tu contraseña: longitud y tipos de caracteres") to
        L("For your password lesson. We never keep the password itself.", "Pour votre leçon sur les mots de passe. Nous ne gardons jamais le mot de passe lui-même.", "Para tu lección de contraseñas. Nunca guardamos la contraseña en sí.")),
    "pw_score" to (L("Your password: strength score", "Votre mot de passe : score de solidité", "Tu contraseña: puntuación de solidez") to
        L("For your password lesson.", "Pour votre leçon sur les mots de passe.", "Para tu lección de contraseñas.")),
    "pw_breach" to (L("Your password: data breach check", "Votre mot de passe : vérification des fuites", "Tu contraseña: comprobación de filtraciones") to
        L("To keep you away from passwords attackers already know.", "Pour éviter les mots de passe que les attaquants connaissent déjà.", "Para evitar contraseñas que los atacantes ya conocen.")),
    "pw_personal" to (L("Your password: personal details inside", "Votre mot de passe : infos personnelles dedans", "Tu contraseña: datos personales dentro") to
        L("For your password lesson.", "Pour votre leçon sur les mots de passe.", "Para tu lección de contraseñas.")),
    "pw_pattern" to (L("Your password: keyboard patterns", "Votre mot de passe : motifs de clavier", "Tu contraseña: patrones de teclado") to
        L("For your password lesson.", "Pour votre leçon sur les mots de passe.", "Para tu lección de contraseñas.")),
    "device" to (L("Device settings", "Réglages de l'appareil", "Ajustes del dispositivo") to
        L("To fit the layout, text size and motion to your device. Nothing else about your device is kept.",
            "Pour adapter la mise en page, la taille du texte et les animations. Rien d'autre sur votre appareil n'est gardé.",
            "Para adaptar el diseño, el tamaño del texto y las animaciones. No guardamos nada más de tu dispositivo.")),
    "mastery" to (L("Skill progress", "Progression par compétence", "Progreso por habilidad") to
        L("To choose your next exercises and how much help to give.", "Pour choisir vos prochains exercices et l'aide à donner.", "Para elegir tus próximos ejercicios y cuánta ayuda dar.")),
    "attempts" to (L("Answered exercises", "Exercices répondus", "Ejercicios respondidos") to
        L("To update your progress, XP and reviews.", "Pour mettre à jour progression, XP et révisions.", "Para actualizar tu progreso, XP y repasos.")),
    "events" to (L("Activity record", "Journal d'activité", "Registro de actividad") to
        L("To track lessons, XP and debriefs (for example: lesson started, lesson completed).",
            "Pour suivre leçons, XP et bilans (par exemple : leçon commencée, leçon terminée).",
            "Para seguir lecciones, XP y resúmenes (por ejemplo: lección empezada, lección terminada).")),
    "field_sends" to (L("Practice emails sent to you", "E-mails d'entraînement envoyés", "Correos de práctica enviados") to
        L("For your debrief. We only record sent, link clicked and reported. Nothing you type.",
            "Pour votre bilan. Nous enregistrons seulement : envoyé, lien cliqué, signalé. Rien de ce que vous tapez.",
            "Para tu resumen. Solo registramos: enviado, enlace pulsado, reportado. Nada de lo que escribes.")),
    "sessions" to (L("Signed-in devices", "Appareils connectés", "Dispositivos con sesión iniciada") to
        L("To keep you signed in.", "Pour garder votre session ouverte.", "Para mantener tu sesión iniciada.")),
    "codes" to (L("Sign-in codes", "Codes de connexion", "Códigos de acceso") to
        L("To confirm your email. Stored only in scrambled form; each is valid 10 minutes.",
            "Pour confirmer votre e-mail. Gardés seulement sous forme brouillée ; chacun est valable 10 minutes.",
            "Para confirmar tu correo. Solo se guardan cifrados; cada uno vale 10 minutos.")),
    "groups" to (L("Groups", "Groupes", "Grupos") to
        L("For team leaderboards (not used yet).", "Pour les classements d'équipe (pas encore utilisés).", "Para clasificaciones de equipo (aún sin uso).")),
)

private val NOT_GIVEN = L("Not given", "Non renseigné", "No indicado")
private val YES = L("Yes", "Oui", "Sí")
private val NO = L("No", "Non", "No")
private val NOT_YET = L("Not yet", "Pas encore", "Todavía no")
private val NONE = L("None", "Aucun", "Ninguno")

private fun date(t: OffsetDateTime?): String? = t?.withOffsetSameInstant(ZoneOffset.UTC)?.toLocalDate()?.toString()
private fun words(code: String) = code.replace('_', ' ')

/** Builds "What we know about you" for one user. Every stored field has a line. Call inside a transaction. */
fun aboutEntries(userId: String): List<AboutEntry> {
    val user = UserData.user(userId)
    val lang = user[Users.language].take(2).lowercase()
    val out = ArrayList<AboutEntry>()
    fun add(key: String, value: String?) {
        val (label, purpose) = F.getValue(key)
        out += AboutEntry(label.of(lang), value?.takeIf { it.isNotBlank() } ?: NOT_GIVEN.of(lang), purpose.of(lang))
    }
    fun yn(b: Boolean) = (if (b) YES else NO).of(lang)
    fun l(en: String, fr: String, es: String) = L(en, fr, es).of(lang)

    // users
    add("email", user[Users.email])
    add("verified", date(user[Users.emailVerifiedAt]) ?: NOT_YET.of(lang))
    add("created", date(user[Users.createdAt]))
    add("first_name", user[Users.firstName])
    add("last_name", user[Users.lastName])
    add("display_name", user[Users.displayName] ?: UserData.displayName(user))
    add("language", user[Users.language])
    add("country", user[Users.country])
    add("password", if (user[Users.passwordHash] != null) {
        l("Set. We keep only a scrambled form (argon2id) that can check it but never turn back into it.",
            "Défini. Nous gardons seulement une forme brouillée (argon2id) qui permet de le vérifier, jamais de le retrouver.",
            "Definida. Solo guardamos una forma cifrada (argon2id) que sirve para comprobarla, nunca para recuperarla.")
    } else NOT_YET.of(lang))
    add("terms", user[Users.termsAcceptedAt]?.let {
        l("Accepted version ${user[Users.termsVersion]} on ${date(it)}", "Version ${user[Users.termsVersion]} acceptée le ${date(it)}",
            "Versión ${user[Users.termsVersion]} aceptada el ${date(it)}")
    } ?: NOT_YET.of(lang))
    add("practice_emails", when (practiceEmailsStatus(user)) {
        "allowed" -> l("On: you asked for one", "Activés : vous en avez demandé un", "Activados: pediste uno")
        "paused" -> l("Paused", "En pause", "En pausa")
        "stopped" -> l("Stopped", "Arrêtés", "Detenidos")
        else -> l("Off (you have not asked for one)", "Désactivés (vous n'en avez pas demandé)", "Desactivados (no has pedido ninguno)")
    })

    // profile json
    val p: Profile = UserData.profile(user)
    add("email_provider", p.emailProvider)
    add("here_for", p.hereFor?.let(::words))
    add("age_range", p.ageRange)
    add("channels", p.channels.joinToString(", ", transform = ::words))
    add("email_app", p.emailApp?.let(::words))
    add("interests", p.interests.joinToString(", ", transform = ::words))
    add("past_experience", listOfNotNull(p.pastExperience?.let(::words), p.pastExperienceText?.takeIf { it.isNotBlank() }?.let { "\"$it\"" })
        .joinToString(": "))
    add("self_rating", p.selfRating?.let { "$it / 5" })
    add("large_text", yn(p.largeText))
    add("dark_mode", when (p.darkMode) {
        null -> l("Follow the device", "Comme l'appareil", "Como el dispositivo")
        true -> yn(true)
        false -> yn(false)
    })
    add("daily_goal", l("${p.dailyMinutes} minutes a day", "${p.dailyMinutes} minutes par jour", "${p.dailyMinutes} minutos al día"))
    add("placement", p.placement?.let {
        l("Level ${it.level}; strongest step: ${words(it.strength.name.lowercase())}; focus: ${words(it.focus.name.lowercase())}",
            "Niveau ${it.level} ; point fort : ${words(it.strength.name.lowercase())} ; à travailler : ${words(it.focus.name.lowercase())}",
            "Nivel ${it.level}; punto fuerte: ${words(it.strength.name.lowercase())}; a trabajar: ${words(it.focus.name.lowercase())}")
    })
    add("status_rating", p.statusRating)

    // password findings (accepted password only)
    val pf = PasswordFindings.selectAll().where { PasswordFindings.userId eq userId }.singleOrNull()
    if (pf == null) {
        for (k in listOf("pw_shape", "pw_score", "pw_breach", "pw_personal", "pw_pattern")) add(k, NONE.of(lang))
    } else {
        val len = pf[PasswordFindings.length]; val cls = pf[PasswordFindings.classes]
        add("pw_shape", l("Your password is $len characters long and uses $cls kinds of characters (checked on ${date(pf[PasswordFindings.analysedAt])}).",
            "Votre mot de passe fait $len caractères et utilise $cls types de caractères (vérifié le ${date(pf[PasswordFindings.analysedAt])}).",
            "Tu contraseña tiene $len caracteres y usa $cls tipos de caracteres (comprobada el ${date(pf[PasswordFindings.analysedAt])})."))
        add("pw_score", l("${pf[PasswordFindings.score]} out of 4", "${pf[PasswordFindings.score]} sur 4", "${pf[PasswordFindings.score]} de 4"))
        add("pw_breach", if (pf[PasswordFindings.pwned]) {
            l("Seen ${pf[PasswordFindings.pwnedCount]} times in known data breaches", "Vu ${pf[PasswordFindings.pwnedCount]} fois dans des fuites connues",
                "Vista ${pf[PasswordFindings.pwnedCount]} veces en filtraciones conocidas")
        } else {
            l("Not found in known data breaches (checked without sending your password anywhere)",
                "Absent des fuites connues (vérifié sans envoyer votre mot de passe nulle part)",
                "No aparece en filtraciones conocidas (comprobado sin enviar tu contraseña a ningún sitio)")
        })
        add("pw_personal", l(
            "Contains your first name: ${yn(pf[PasswordFindings.containsName])}; your email name: ${yn(pf[PasswordFindings.containsEmail])}; a year: ${yn(pf[PasswordFindings.containsYear])}",
            "Contient votre prénom : ${yn(pf[PasswordFindings.containsName])} ; le nom de votre e-mail : ${yn(pf[PasswordFindings.containsEmail])} ; une année : ${yn(pf[PasswordFindings.containsYear])}",
            "Contiene tu nombre: ${yn(pf[PasswordFindings.containsName])}; el nombre de tu correo: ${yn(pf[PasswordFindings.containsEmail])}; un año: ${yn(pf[PasswordFindings.containsYear])}",
        ))
        add("pw_pattern", pf[PasswordFindings.keyboardPattern]?.let {
            l("Contains a keyboard pattern (${words(it)})", "Contient un motif de clavier (${words(it)})", "Contiene un patrón de teclado (${words(it)})")
        } ?: NONE.of(lang))
    }

    // client signals
    val sig = ClientSignals.selectAll().where { ClientSignals.userId eq userId }.toList()
    add("device", sig.joinToString(" | ") { row ->
        val json = runCatching { TeaJson.decodeFromString(JsonObject.serializer(), row[ClientSignals.json]) }.getOrNull() ?: JsonObject(emptyMap())
        (listOf("platform: ${row[ClientSignals.platform]}") +
            json.map { (k, v) -> "${words(k)}: ${(v as? JsonPrimitive)?.content ?: v}" }).joinToString(", ")
    }.ifEmpty { NONE.of(lang) })

    // learning data
    val mastery = loadMastery(userId)
    add("mastery", if (mastery.isEmpty()) NONE.of(lang) else
        mastery.joinToString(", ") { "${words(it.skill.name.lowercase())} ${(it.p * 100).toInt()}%" })
    add("attempts", Attempts.selectAll().where { Attempts.userId eq userId }.count().toString())
    add("events", Events.selectAll().where { Events.userId eq userId }.count().toString())

    val sends = FieldSends.selectAll().where { FieldSends.userId eq userId }.toList()
    val sent = sends.count { it[FieldSends.sentAt] != null }
    val clicked = sends.count { it[FieldSends.clickedAt] != null }
    val reported = sends.count { it[FieldSends.reportedAt] != null }
    val pending = sends.count { it[FieldSends.sentAt] == null && it[FieldSends.cancelledAt] == null }
    add("field_sends", l("Sent: $sent, link clicked: $clicked, reported: $reported, waiting to be sent: $pending",
        "Envoyés : $sent, lien cliqué : $clicked, signalés : $reported, en attente : $pending",
        "Enviados: $sent, enlace pulsado: $clicked, reportados: $reported, pendientes: $pending"))

    val activeSessions = Sessions.selectAll().where { (Sessions.userId eq userId) and Sessions.revokedAt.isNull() }.count()
    add("sessions", activeSessions.toString())
    add("codes", EmailCodes.selectAll().where { EmailCodes.email eq user[Users.email] }.count().toString())
    add("groups", GroupMembers.selectAll().where { GroupMembers.userId eq userId }.count().toString())
    return out
}

/** Hard delete of every row about the user, in every table, then the user. Call inside a transaction. */
fun deleteUser(userId: String) {
    val email = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.email)
    Attempts.deleteWhere { Attempts.userId eq userId }
    Mastery.deleteWhere { Mastery.userId eq userId }
    Events.deleteWhere { Events.userId eq userId }
    Profiles.deleteWhere { Profiles.userId eq userId }
    PasswordFindings.deleteWhere { PasswordFindings.userId eq userId }
    ClientSignals.deleteWhere { ClientSignals.userId eq userId }
    FieldSends.deleteWhere { FieldSends.userId eq userId }
    GroupMembers.deleteWhere { GroupMembers.userId eq userId }
    Sessions.deleteWhere { Sessions.userId eq userId }
    if (email != null) EmailCodes.deleteWhere { EmailCodes.email eq email }
    Users.deleteWhere { Users.id eq userId }
}

fun Route.aboutRoutes() {
    get("/me/about") {
        val s = call.services
        val session = call.requireUser()
        call.respond(s.db.tx { aboutEntries(session.userId) })
    }

    delete("/me") {
        val s = call.services
        val session = call.requireUser()
        s.db.tx { deleteUser(session.userId) }
        call.respond(HttpStatusCode.NoContent)
    }
}
