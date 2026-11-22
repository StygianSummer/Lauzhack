package tea.ui.components

import tea.model.EmailContent
import tea.model.Hotspot

/** A tappable part of a mock email. */
sealed interface EmailPart {
    data object FromName : EmailPart
    data object FromAddress : EmailPart
    data object ReplyTo : EmailPart
    data object Subject : EmailPart
    data class Body(val index: Int) : EmailPart
    data class Attachment(val index: Int) : EmailPart
}

/** Region ids of the two non-flag decoys (plan §9.3). Hotspot regions use the hotspot id. */
object EmailDecoys {
    const val GREETING = "decoy:greeting"
    const val SIGNATURE = "decoy:signature"
    fun isDecoy(regionId: String): Boolean = regionId.startsWith("decoy:")
}

/**
 * Resolves a hotspot `target` (grammar on [Hotspot]) to a part of [email], or null if it does not
 * exist. "link" is the first body block with `t == "link"`.
 */
fun EmailContent.partFor(target: String): EmailPart? {
    val part = when {
        target == "from_name" -> EmailPart.FromName
        target == "from_address" -> EmailPart.FromAddress
        target == "reply_to" -> if (replyTo != null) EmailPart.ReplyTo else null
        target == "subject" -> EmailPart.Subject
        target == "link" -> body.indexOfFirst { it.t == "link" }.takeIf { it >= 0 }?.let { EmailPart.Body(it) }
        target.startsWith("body:") -> target.removePrefix("body:").toIntOrNull()?.let { EmailPart.Body(it) }
        target.startsWith("attachment:") -> target.removePrefix("attachment:").toIntOrNull()?.let { EmailPart.Attachment(it) }
        else -> null
    }
    return when (part) {
        is EmailPart.Body -> part.takeIf { it.index in body.indices }
        is EmailPart.Attachment -> part.takeIf { it.index in attachments.indices }
        else -> part
    }
}

/**
 * Which region id each part of [email] answers to: hotspot targets (first hotspot wins), body
 * blocks that carry `hotspot = <id>`, and with [decoys] the greeting (first text block) and the
 * signature block, when they are not already hotspots.
 */
fun emailRegions(email: EmailContent, hotspots: List<Hotspot>, decoys: Boolean): Map<EmailPart, String> {
    val map = LinkedHashMap<EmailPart, String>()
    for (h in hotspots) {
        val part = email.partFor(h.target) ?: continue
        map.getOrPut(part) { h.id }
    }
    val known = hotspots.map { it.id }.toSet()
    email.body.forEachIndexed { i, block ->
        val id = block.hotspot ?: return@forEachIndexed
        if (id in known) map.getOrPut(EmailPart.Body(i)) { id }
    }
    if (decoys) {
        val greeting = email.body.indexOfFirst { it.t == "text" }
        if (greeting >= 0) map.getOrPut(EmailPart.Body(greeting)) { EmailDecoys.GREETING }
        val signature = email.body.indexOfLast { it.t == "signature" }
        if (signature >= 0) map.getOrPut(EmailPart.Body(signature)) { EmailDecoys.SIGNATURE }
    }
    return map
}
