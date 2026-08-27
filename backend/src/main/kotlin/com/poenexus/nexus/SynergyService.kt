package com.poenexus.nexus

import com.poenexus.pob.AuraStatsDto
import com.poenexus.pob.GemTaxonomy
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple
import java.util.UUID

/**
 * Модуль 2: что каждый участник несёт в пати:
 * ауры/проклятия с тотал-уровнем, аура-паспорт, HP/ES/Mana (важно для Mana Guard).
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
            val parsed = r.getString("parsed_data")?.let { runCatching { JsonObject(it) }.getOrNull() }
            val items = parsed?.getJsonArray("items")?.map { it as JsonObject } ?: emptyList()
            val gearJson = parsed?.getJsonObject("gear") ?: JsonObject()
            val gear = gearJson.fieldNames().associateWith { gearJson.getString(it) }

            val auras = mutableListOf<AuraGemDto>()
            val curses = mutableListOf<AuraGemDto>()
            parsed?.getJsonArray("gems")?.let { gems ->
                for (i in 0 until gems.size()) {
                    val g = gems.getJsonObject(i) ?: continue
                    val name = g.getString("name") ?: continue
                    val total = gemTotalLevel(g, items, gear)
                    when (name) {
                        in GemTaxonomy.AURAS -> {
                            auras += AuraGemDto(name, total)
                            auraCounts[name] = (auraCounts[name] ?: 0) + 1
                        }
                        in GemTaxonomy.CURSES -> {
                            curses += AuraGemDto(name, total)
                            curseCounts[name] = (curseCounts[name] ?: 0) + 1
                        }
                    }
                }
            }

            val stats = parsed?.getJsonObject("stats") ?: JsonObject()
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
                aura,
                life = stats.getDouble("Life")?.toInt() ?: 0,
                energyShield = stats.getDouble("EnergyShield")?.toInt() ?: 0,
                mana = stats.getDouble("Mana")?.toInt() ?: 0
            )
        }

        val duplicates = auraCounts.filterValues { it > 1 }.keys +
            curseCounts.filterValues { it > 1 }.keys
        return SynergyDto(members, auraCounts, curseCounts, duplicates.toList())
    }

    /**
     * Тотал-уровень гема: база из XML + бонусы "+N to Level of Socketed ..."
     * из предмета того слота, где стоит гем.
     */
    private fun gemTotalLevel(gem: JsonObject, items: List<JsonObject>, gear: Map<String, String>): Int {
        val base = gem.getInteger("level") ?: 1
        val slot = gem.getString("slot") ?: return base
        val itemName = gear[slot] ?: return base
        val item = items.firstOrNull { it.getString("name") == itemName } ?: return base
        val name = gem.getString("name") ?: ""
        val isAura = name in GemTaxonomy.AURAS
        val isCurse = name in GemTaxonomy.CURSES
        val mods = item.getJsonArray("mods")?.map { it.toString() } ?: emptyList()
        var bonus = 0
        for (m in mods) {
            bonus += when {
                m.contains("to Level of Socketed Gems") -> plusNum(m)
                isAura && m.contains("to Level of Socketed Aura Gems") -> plusNum(m)
                isCurse && m.contains("to Level of Socketed Curse Gems") -> plusNum(m)
                (isAura || isCurse) && m.contains("to Level of Socketed Spell Gems") -> plusNum(m)
                else -> 0
            }
        }
        return base + bonus
    }

    private fun plusNum(m: String): Int =
        Regex("\\+(\\d+)").find(m)?.groupValues?.get(1)?.toInt() ?: 0
}