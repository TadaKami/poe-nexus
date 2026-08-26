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

data class DiffReport(
    val entries: List<DiffEntry>,
    val missingPassives: List<PassiveNodeInfo>,
    val levelGap: Int
)