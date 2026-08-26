package com.poenexus.nexus

import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple
import java.util.UUID

/**
 * Модуль 2: что каждый участник несёт в пати (ауры/проклятия)
 * из его сохранённого current-PoB.
 */
class SynergyService(private val pool: PgPool) {

    companion object {
        val AURAS = setOf(
            "Malevolence", "Discipline", "Flesh and Stone", "Tempest Shield", "Haste", "Grace",
            "Wrath", "Zealotry", "Anger", "Hatred", "Pride", "Vitality", "Clarity",
            "Determination", "Precision", "Purity of Elements", "Purity of Fire",
            "Purity of Ice", "Purity of Lightning", "War Banner", "Defiance Banner", "Battlemage's Cry"
        )
        val CURSES = setOf(
            "Enfeeble", "Despair", "Punishment", "Temporal Chains", "Vulnerability",
            "Elemental Weakness", "Flammability", "Frostbite", "Conductivity",
            "Assassin's Mark", "Warlord's Mark", "Sniper's Mark"
        )
    }

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
            val parsed = r.getJsonObject("parsed_data")
            parsed?.getJsonArray("gems")?.let { gems ->
                for (i in 0 until gems.size()) {
                    val name = gems.getJsonObject(i)?.getString("name") ?: continue
                    when (name) {
                        in AURAS -> { auras += name; auraCounts[name] = (auraCounts[name] ?: 0) + 1 }
                        in CURSES -> { curses += name; curseCounts[name] = (curseCounts[name] ?: 0) + 1 }
                    }
                }
            }
            members += MemberSynergyDto(
                r.getValue("user_id").toString(),
                r.getString("email"),
                parsed != null,
                auras,
                curses
            )
        }

        val duplicates = auraCounts.filterValues { it > 1 }.keys +
            curseCounts.filterValues { it > 1 }.keys
        return SynergyDto(members, auraCounts, curseCounts, duplicates.toList())
    }
}