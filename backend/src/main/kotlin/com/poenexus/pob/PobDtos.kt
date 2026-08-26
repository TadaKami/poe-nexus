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
    val stats: Map<String, Double>
)

data class PobBuildDto(
    val scope: String,
    val pastebinUrl: String,
    val versionHash: String,
    val parsed: PobNormalized
)

data class DiffEntry(
    val category: String,   // gem | passive | config | class
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
    val score: Int,               // 0..100 — насколько current близок к таргету
    val missingMods: List<String>
)

data class TreeNodeDto(
    val id: Int,
    val x: Double,
    val y: Double,
    val kind: String,             // normal | notable | keystone
    val icon: String?,
    val name: String?
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