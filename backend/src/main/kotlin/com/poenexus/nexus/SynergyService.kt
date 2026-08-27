package com.poenexus.nexus

import com.poenexus.pob.AuraStatsDto
import com.poenexus.pob.GemTaxonomy
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple
import java.util.UUID

/**
 * Модуль 2: что каждый участник несёт в пати (ауры/проклятия/аура-паспорт)
 * из его сохранённого current-PoB.
 */
class SynergyService(private val pool: PgPool) {

    suspend fun synergy(nexusId: String): SynergyDto {
        val rows = pool.preparedQuery(
            """SELECT u.id AS user_id, u.email, b.parsed_data
               FROM nexus_members m
               JOIN users u ON u.id = m.user_id
               LEFT JOIN pob_builds b ON b.user_id = m.user_id AND b.scope = 'current'
               WHERE m.nexus_id = $1"""
        ).execute(Tuple.of(UUID.fromString(nexusId))).await()

        val members = mutableListOf<MemberSynergyDto>()
        val auraCounts = mutableMapOf<String, Int>()
        val curseCounts = mutableMapOf<String, Int>()

        for (r in rows) {
            val auras = mutableListOf<String>()
            val curses = mutableListOf<String>()
            // pg-client в нашем сетапе отдаёт jsonb как String
            val parsed = r.getString("parsed_data")?.let { runCatching { JsonObject(it) }.getOrNull() }
            parsed?.getJsonArray("gems")?.let { gems ->
                for (i in 0 until gems.size()) {
                    val name = gems.getJsonObject(i)?.getString("name") ?: continue
                    when (name) {
                        in GemTaxonomy.AURAS -> { auras += name; auraCounts[name] = (auraCounts[name] ?: 0) + 1 }
                        in GemTaxonomy.CURSES -> { curses += name; curseCounts[name] = (curseCounts[name] ?: 0) + 1 }
                    }
                }
            }
            val aura = parsed?.getJsonObject("aura")?.let { a ->
                AuraStatsDto(
                    a.getBoolean("auraBot") ?: false,
                    a.getInteger("auraEffect") ?: 0,
                    a.getInteger("areaEffect") ?: 0,
                    a.getInteger("reservationEff") ?: 0,
                    a.getInteger("fireResist") ?: 0,
                    a.getInteger("coldResist") ?: 0,
                    a.getInteger("lightResist") ?: 0,
                    a.getInteger("chaosResist") ?: 0,
                    a.getInteger("maxResist") ?: 0
                )
            }
            members += MemberSynergyDto(
                r.getValue("user_id").toString(),
                r.getString("email"),
                parsed != null,
                auras,
                curses,
                aura
            )
        }

        val duplicates = auraCounts.filterValues { it > 1 }.keys +
            curseCounts.filterValues { it > 1 }.keys
        return SynergyDto(members, auraCounts, curseCounts, duplicates.toList())
    }
}