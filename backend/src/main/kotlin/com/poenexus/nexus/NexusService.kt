package com.poenexus.nexus

import com.poenexus.http.HttpError
import com.poenexus.infra.Db.inTransaction
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

class NexusService(private val pool: PgPool) {

    private val random = SecureRandom()

    suspend fun list(userId: String): List<NexusDto> =
        pool.preparedQuery(
            """SELECT n.id, n.name, n.description, n.leader_id,
                      (SELECT count(*) FROM nexus_members m2 WHERE m2.nexus_id = n.id) AS member_count
               FROM nexuses n
               JOIN nexus_members m ON m.nexus_id = n.id AND m.user_id = $1
               ORDER BY n.created_at"""
        ).execute(Tuple.of(UUID.fromString(userId))).await().map { r -> toNexusDto(r) }

    suspend fun create(userId: String, name: String, description: String?): NexusDto {
        val trimmed = name.trim()
        if (trimmed.length < 3 || trimmed.length > 100)
            throw HttpError(400, "invalid_name", "Name must be 3..100 characters")

        val uid = UUID.fromString(userId)
        val row = pool.inTransaction { conn ->
            val n = conn.preparedQuery(
                """INSERT INTO nexuses (name, description, leader_id)
                   VALUES ($1, $2, $3) RETURNING id, name, description, leader_id"""
            ).execute(Tuple.of(trimmed, description, uid)).await().first()
            conn.preparedQuery(
                "INSERT INTO nexus_members (nexus_id, user_id, role) VALUES ($1, $2, 'leader')"
            ).execute(Tuple.of(n.getValue("id"), uid)).await()
            n
        }
        return NexusDto(
            row.getValue("id").toString(), row.getString("name"),
            row.getString("description"), row.getValue("leader_id").toString(), 1
        )
    }

    suspend fun details(userId: String, nexusId: String): NexusDetailsDto {
        requireMember(userId, nexusId)
        val nid = UUID.fromString(nexusId)
        val n = pool.preparedQuery("SELECT id, name, description, leader_id FROM nexuses WHERE id = $1")
            .execute(Tuple.of(nid)).await().firstOrNull()
            ?: throw HttpError(404, "not_found", "Nexus not found")

        val members = pool.preparedQuery(
            """SELECT m.user_id, m.role, m.joined_at, u.email
               FROM nexus_members m JOIN users u ON u.id = m.user_id
               WHERE m.nexus_id = $1 ORDER BY m.joined_at"""
        ).execute(Tuple.of(nid)).await().map { r ->
            MemberDto(
                r.getValue("user_id").toString(),
                r.getString("email"),
                r.getString("role"),
                r.getOffsetDateTime("joined_at").toString()
            )
        }
        return NexusDetailsDto(
            NexusDto(n.getValue("id").toString(), n.getString("name"), n.getString("description"),
                n.getValue("leader_id").toString(), members.size),
            members
        )
    }

    suspend fun invite(userId: String, nexusId: String): InviteDto {
        val role = requireMember(userId, nexusId)
        if (role == "member") throw HttpError(403, "forbidden", "Leader or officer required")

        val code = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(16).also(random::nextBytes))
        val expires = OffsetDateTime.now().plusDays(7)
        pool.preparedQuery(
            "INSERT INTO nexus_invites (nexus_id, code, created_by, expires_at) VALUES ($1, $2, $3, $4)"
        ).execute(Tuple.of(UUID.fromString(nexusId), code, UUID.fromString(userId), expires)).await()
        return InviteDto(code, expires.toString())
    }

    suspend fun join(userId: String, code: String): NexusDto {
        val inv = pool.preparedQuery(
            "SELECT id, nexus_id, used_count, max_uses FROM nexus_invites WHERE code = $1 AND expires_at > now()"
        ).execute(Tuple.of(code.trim())).await().firstOrNull()
            ?: throw HttpError(404, "invalid_code", "Invite code invalid or expired")

        if (inv.getInteger("used_count") >= (inv.getInteger("max_uses") ?: Int.MAX_VALUE))
            throw HttpError(410, "code_exhausted", "Invite code already used")

        val nid = inv.getValue("nexus_id") as UUID
        val uid = UUID.fromString(userId)

        val already = pool.preparedQuery("SELECT 1 FROM nexus_members WHERE nexus_id = $1 AND user_id = $2")
            .execute(Tuple.of(nid, uid)).await()
        if (already.size() > 0) throw HttpError(409, "already_member", "You are already in this Nexus")

        pool.inTransaction { conn ->
            conn.preparedQuery("INSERT INTO nexus_members (nexus_id, user_id, role) VALUES ($1, $2, 'member')")
                .execute(Tuple.of(nid, uid)).await()
            conn.preparedQuery("UPDATE nexus_invites SET used_count = used_count + 1 WHERE id = $1")
                .execute(Tuple.of(inv.getValue("id"))).await()
        }
        // TODO(Фаза 2): vertx.eventBus().publish("nexus.{id}.members.changed", ...)
        return nexusDto(nid.toString())
    }

    suspend fun changeRole(actorId: String, nexusId: String, targetId: String, newRole: String) {
        if (requireMember(actorId, nexusId) != "leader") throw HttpError(403, "forbidden", "Leader only")
        if (newRole !in setOf("officer", "member")) throw HttpError(400, "invalid_role", "Role must be officer or member")
        if (requireMember(targetId, nexusId) == "leader") throw HttpError(403, "forbidden", "Cannot change leader role")
        pool.preparedQuery("UPDATE nexus_members SET role = $1 WHERE nexus_id = $2 AND user_id = $3")
            .execute(Tuple.of(newRole, UUID.fromString(nexusId), UUID.fromString(targetId))).await()
    }

    suspend fun kick(actorId: String, nexusId: String, targetId: String) {
        val actorRole = requireMember(actorId, nexusId)
        if (actorRole == "member") throw HttpError(403, "forbidden", "Leader or officer required")
        val targetRole = requireMember(targetId, nexusId)
        if (targetRole == "leader") throw HttpError(403, "forbidden", "Cannot kick leader")
        if (targetRole == "officer" && actorRole != "leader") throw HttpError(403, "forbidden", "Only leader can kick officer")
        pool.preparedQuery("DELETE FROM nexus_members WHERE nexus_id = $1 AND user_id = $2")
            .execute(Tuple.of(UUID.fromString(nexusId), UUID.fromString(targetId))).await()
    }

    suspend fun delete(actorId: String, nexusId: String) {
        if (requireMember(actorId, nexusId) != "leader") throw HttpError(403, "forbidden", "Leader only")
        pool.preparedQuery("DELETE FROM nexuses WHERE id = $1").execute(Tuple.of(UUID.fromString(nexusId))).await()
    }

    // ---------- internals ----------

    private suspend fun requireMember(userId: String, nexusId: String): String =
        pool.preparedQuery("SELECT role FROM nexus_members WHERE nexus_id = $1 AND user_id = $2")
            .execute(Tuple.of(UUID.fromString(nexusId), UUID.fromString(userId))).await()
            .firstOrNull()?.getString("role")
            ?: throw HttpError(403, "not_member", "You are not a member of this Nexus")

    private suspend fun nexusDto(nexusId: String): NexusDto =
        pool.preparedQuery(
            """SELECT n.id, n.name, n.description, n.leader_id,
                      (SELECT count(*) FROM nexus_members m WHERE m.nexus_id = n.id) AS member_count
               FROM nexuses n WHERE n.id = $1"""
        ).execute(Tuple.of(UUID.fromString(nexusId))).await().firstOrNull()?.let { toNexusDto(it) }
            ?: throw HttpError(404, "not_found", "Nexus not found")

    private fun toNexusDto(r: io.vertx.sqlclient.Row) = NexusDto(
        r.getValue("id").toString(),
        r.getString("name"),
        r.getString("description"),
        r.getValue("leader_id").toString(),
        r.getLong("member_count").toInt()
    )
}