package tea.server.me

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tea.model.MasteryState
import tea.model.Skill
import tea.model.Support
import tea.server.auth.requireUser
import tea.server.badRequest
import tea.server.db.Mastery
import tea.server.db.toKInstant
import tea.server.db.toOdt
import tea.server.services

private fun masteryFromRow(row: ResultRow) = MasteryState(
    skill = decodeWire(Skill.serializer(), row[Mastery.skill]),
    p = row[Mastery.p],
    level = row[Mastery.level],
    attempts = row[Mastery.attempts],
    unaided = row[Mastery.unaided],
    support = decodeWire(Support.serializer(), row[Mastery.support]),
    testedOut = row[Mastery.testedOut],
    nextReviewAt = row[Mastery.nextReviewAt]?.toKInstant(),
    reviewInterval = row[Mastery.reviewInterval],
    updatedAt = row[Mastery.updatedAt].toKInstant(),
)

fun loadMastery(userId: String): List<MasteryState> =
    Mastery.selectAll().where { Mastery.userId eq userId }.map(::masteryFromRow).sortedBy { it.skill.ordinal }

fun Route.masteryRoutes() {
    get("/me/mastery") {
        val s = call.services
        val session = call.requireUser()
        call.respond(s.db.tx { loadMastery(session.userId) })
    }

    /** Last-write-wins per skill by `updated_at`; answers with the merged list. */
    put("/me/mastery") {
        val s = call.services
        val session = call.requireUser()
        val incoming = call.receive<List<MasteryState>>()
        if (incoming.size > 100) throw badRequest("too_many", "Too many mastery rows.")
        val merged = s.db.tx {
            // If the same skill appears twice in one request, the newest one wins.
            val newest = incoming.groupBy { it.skill }.mapValues { (_, v) -> v.maxBy { it.updatedAt } }.values
            for (m in newest) {
                val skill = m.skill.wire(Skill.serializer())
                val where = { (Mastery.userId eq session.userId) and (Mastery.skill eq skill) }
                val current = Mastery.selectAll().where(where).singleOrNull()
                if (current != null && current[Mastery.updatedAt].toKInstant() >= m.updatedAt) continue
                val values: (org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>) -> Unit = {
                    it[Mastery.p] = m.p
                    it[Mastery.level] = m.level
                    it[Mastery.attempts] = m.attempts
                    it[Mastery.unaided] = m.unaided
                    it[Mastery.support] = m.support.wire(Support.serializer())
                    it[Mastery.testedOut] = m.testedOut
                    it[Mastery.reviewInterval] = m.reviewInterval
                    it[Mastery.nextReviewAt] = m.nextReviewAt?.toOdt()
                    it[Mastery.updatedAt] = m.updatedAt.toOdt()
                }
                if (current == null) {
                    Mastery.insert { it[userId] = session.userId; it[Mastery.skill] = skill; values(it) }
                } else {
                    Mastery.update(where) { values(it) }
                }
            }
            loadMastery(session.userId)
        }
        call.respond(merged)
    }
}
