package tea.app

/**
 * Screen <-> URL hash (plan §9.1), e.g. `#/home`, `#/lesson/email-01/2`, `#/gotcha/<sendId>`.
 * The email address of [Screen.Code] is never put in the URL: `#/code` maps back to the address
 * waiting for its code.
 */
object Routes {
    fun toHash(screen: Screen): String = when (screen) {
        Screen.Language -> "/language"
        Screen.Email -> "/email"
        is Screen.Code -> "/code"
        Screen.Name -> "/name"
        Screen.Password -> "/password"
        is Screen.Onboarding -> "/onboarding/${screen.q}"
        Screen.Terms -> "/terms"
        Screen.PlacementIntro -> "/placement"
        Screen.Placement -> "/placement/run"
        Screen.PlacementResult -> "/placement/result"
        Screen.Home -> "/home"
        is Screen.Lesson -> "/lesson/${screen.id}/${screen.section}"
        is Screen.Gotcha -> "/gotcha/${screen.sendId}"
        is Screen.Practice -> if (screen.focusWeakest) "/practice/focus" else "/practice"
        Screen.PracticeSummary -> "/practice/summary"
        is Screen.Reveal -> "/reveal/${screen.token}"
        Screen.About -> "/about"
        Screen.Settings -> "/settings"
        Screen.Leaderboard -> "/leaderboard"
        Screen.Gallery -> "/gallery"
    }

    /** Parses a hash (with or without '#'); null when it names no screen. */
    fun parse(
        hash: String,
        pendingEmail: String? = null,
        pendingGotchas: Map<String, String> = emptyMap(),
    ): Screen? {
        val parts = hash.removePrefix("#").trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val rest = parts.drop(1)
        return when (parts[0]) {
            "language" -> Screen.Language
            "email" -> Screen.Email
            "code" -> pendingEmail?.let { Screen.Code(it) } ?: Screen.Email
            "name" -> Screen.Name
            "password" -> Screen.Password
            "onboarding" -> Screen.Onboarding(rest.firstOrNull()?.toIntOrNull()?.coerceAtLeast(0) ?: 0)
            "terms" -> Screen.Terms
            "placement" -> when (rest.firstOrNull()) {
                null -> Screen.PlacementIntro
                "run" -> Screen.Placement
                "result" -> Screen.PlacementResult
                else -> null
            }
            "home" -> Screen.Home
            "lesson" -> rest.firstOrNull()?.let { Screen.Lesson(it, rest.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
            "gotcha" -> rest.firstOrNull()?.let { sendId ->
                pendingGotchas.entries.firstOrNull { it.value == sendId }?.key?.let { Screen.Gotcha(sendId, it) }
            }
            "practice" -> when (rest.firstOrNull()) {
                null -> Screen.Practice()
                "focus" -> Screen.Practice(focusWeakest = true)
                "summary" -> Screen.PracticeSummary
                else -> null
            }
            "reveal" -> rest.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Screen.Reveal(it) }
            "about" -> Screen.About
            "settings" -> Screen.Settings
            "leaderboard" -> Screen.Leaderboard
            "gallery" -> Screen.Gallery
            else -> null
        }
    }

    fun isGotchaRoute(hash: String): Boolean =
        hash.removePrefix("#").trim('/').split('/').firstOrNull() == "gotcha"

    /** Registration screens: not reachable by URL once the terms are accepted. */
    fun isRegistration(screen: Screen): Boolean = when (screen) {
        Screen.Language, Screen.Email, is Screen.Code, Screen.Name, Screen.Password,
        is Screen.Onboarding, Screen.Terms -> true
        else -> false
    }
}
