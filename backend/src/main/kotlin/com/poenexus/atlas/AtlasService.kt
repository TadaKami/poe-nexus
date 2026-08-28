package com.poenexus.atlas

import com.poenexus.http.HttpError
import com.poenexus.ws.Events
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple
import java.util.UUID

class AtlasService(
    private val vertx: Vertx,
    private val pool: PgPool,
    private val tree: AtlasTreeDataService
) {

    suspend fun save(userId: String, url: String): JsonObject {
        val hash = AtlasHashDecoder.extractHash(url)
        if (hash.isBlank()) throw HttpError(400, "bad_atlas_url", "Cannot extract atlas hash from URL")
        val known = tree.nodeIds()
        val best = AtlasHashDecoder.decodeCandidates(hash)
            .maxByOrNull { (_, ids) -> ids.count { it in known } }
            ?.second ?: emptyList()
        pool.preparedQuery(
            """INSERT INTO atlas_builds(user_id, url, node_ids, updated_at)
               VALUES ($1, $2, $3, now())
               ON CONFLICT (user_id) DO UPDATE SET
                 url = EXCLUDED.url, node_ids = EXCLUDED.node_ids, updated_at = now()"""
        ).execute(Tuple.of(UUID.fromString(userId), url, JsonArray(best))).await()
        return JsonObject().put("url", url).put("nodeCount", best.size)
    }

    suspend fun get(userId: String): JsonObject? =
        pool.preparedQuery("SELECT url, node_ids FROM atlas_builds WHERE user_id = $1")
            .execute(Tuple.of(UUID.fromString(userId))).await().firstOrNull()?.let { r ->
                JsonObject()
                    .put("url", r.getString("url"))
                    .put("nodeIds", r.getJsonArray("node_ids") ?: JsonArray())
            }

    suspend fun nexusAtlases(nexusId: String): JsonArray =
        pool.preparedQuery(
            """SELECT u.email, a.url, a.node_ids
               FROM nexus_members m
               JOIN users u ON u.id = m.user_id
               LEFT JOIN atlas_builds a ON a.user_id = m.user_id
               WHERE m.nexus_id = $1"""
        ).execute(Tuple.of(UUID.fromString(nexusId))).await().let { rows ->
            val arr = JsonArray()
            for (r in rows) {
                arr.add(
                    JsonObject()
                        .put("email", r.getString("email"))
                        .put("url", r.getString("url"))
                        .put("nodeIds", r.getJsonArray("node_ids") ?: JsonArray())
                )
            }
            arr
        }

    suspend fun treePayload() = tree.payload()

    // ---------- Общий аллок атласа Нексуса ----------

    suspend fun getAlloc(nexusId: String): JsonObject {
        val r = pool.preparedQuery(
            "SELECT node_ids FROM atlas_allocations WHERE nexus_id = $1"
        ).execute(Tuple.of(UUID.fromString(nexusId))).await().firstOrNull()
        return JsonObject().put("nodeIds", r?.getJsonArray("node_ids") ?: JsonArray())
    }

    /** Пик/анпик ноды любым участником; результат рассылается всем. */
    suspend fun toggle(nexusId: String, userId: String, add: List<Int>, remove: List<Int>): JsonObject {
        requireMember(nexusId, userId)
        val nid = UUID.fromString(nexusId)
        val existing = pool.preparedQuery(
            "SELECT node_ids FROM atlas_allocations WHERE nexus_id = $1"
        ).execute(Tuple.of(nid)).await().firstOrNull()?.getJsonArray("node_ids") ?: JsonArray()
        val set = mutableSetOf<Int>()
        for (i in 0 until existing.size()) set += existing.getInteger(i)
        set.removeAll(remove.toSet())
        set.addAll(add)
        val nodeIds = JsonArray(set.toList().sorted())
        pool.preparedQuery(
            """INSERT INTO atlas_allocations(nexus_id, node_ids, updated_by, updated_at)
               VALUES ($1, $2, $3, now())
               ON CONFLICT (nexus_id) DO UPDATE SET
                 node_ids = EXCLUDED.node_ids, updated_by = EXCLUDED.updated_by, updated_at = now()"""
        ).execute(Tuple.of(nid, nodeIds, UUID.fromString(userId))).await()
        Events.users(
            vertx, memberIds(nexusId), "atlas.changed",
            JsonObject().put("nexusId", nexusId).put("nodeIds", nodeIds)
        )
        return JsonObject().put("nodeIds", nodeIds)
    }

    // ---------- internals ----------

    private suspend fun memberIds(nexusId: String): List<String> =
        pool.preparedQuery("SELECT user_id FROM nexus_members WHERE nexus_id = $1")
            .execute(Tuple.of(UUID.fromString(nexusId))).await()
            .map { it.getValue("user_id").toString() }

    private suspend fun requireMember(nexusId: String, userId: String) {
        val r = pool.preparedQuery(
            "SELECT 1 FROM nexus_members WHERE nexus_id = $1 AND user_id = $2"
        ).execute(Tuple.of(UUID.fromString(nexusId), UUID.fromString(userId))).await()
        if (r.size() == 0) throw HttpError(403, "not_member", "You are not a member of this Nexus")
    }
}