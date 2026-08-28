package com.poenexus.pob

data class GemDto(
    val name: String,
    val skillId: String?,
    val level: Int,
    val quality: Int,
    val enabled: Boolean,
    val slot: String?
)

data class ItemDto(
    val id: Int,
    val rarity: String,
    val name: String,
    val base: String,
    val mods: List<String>
)

data class PobNormalized(
    val level: Int?,
    val className: String?,
    val ascendancy: String?,
    val gems: List<GemDto>,
    val passiveNodeIds: List<Int>,
    val treeVersion: String?,
    val overrides: List<String>,
    val items: List<ItemDto>,
    val gear: Map<String, String>,
    val config: Map<String, String>,
    val stats: Map<String, Double>,
    val aura: AuraStatsDto? = null
)

data class PobBuildDto(
    val scope: String,
    val pastebinUrl: String,
    val versionHash: String,
    val parsed: PobNormalized
)

data class DiffEntry(
    val category: String,
    val message: String
)

data class PassiveNodeInfo(
    val id: Int,
    val name: String,
    val effects: List<String>,
    val icon: String?,
    val keystone: Boolean,
    val notable: Boolean
)

data class GearScoreDto(
    val slot: String,
    val currentName: String?,
    val targetName: String,
    val score: Int,
    val missingMods: List<String>
)

data class TreeNodeDto(
    val id: Int,
    val x: Double,
    val y: Double,
    val kind: String,
    val icon: String?,
    val name: String?,
    val sd: List<String> = emptyList()
)

data class TreePayload(
    val nodes: List<TreeNodeDto>,
    val edges: List<List<Int>>
)

data class DiffReport(
    val entries: List<DiffEntry>,
    val missingPassives: List<PassiveNodeInfo>,
    val missingPassiveIds: List<Int>,
    val gearScores: List<GearScoreDto>,
    val levelGap: Int
)

/** Единый словарь аур/проклятий (парсер + синергия). */
object GemTaxonomy {
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
    /** Восхождения, которые обычно играют роль аура-бота. */
    val AURA_BOT_ASCENDANCIES = setOf("Champion", "Ascendant", "Luminary")
}

/** Паспорт аура-бота. auraEffect/areaEffect/resEff — суммарный increased% (дерево+шмот+графты). */
data class AuraStatsDto(
    val auraBot: Boolean,
    val auraEffect: Int,
    val areaEffect: Int,
    val reservationEff: Int,
    val fireResist: Int,
    val coldResist: Int,
    val lightResist: Int,
    val chaosResist: Int,
    val maxResist: Int
)