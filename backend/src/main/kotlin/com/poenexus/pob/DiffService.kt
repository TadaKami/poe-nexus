package com.poenexus.pob
 
import kotlin.math.abs
import kotlin.math.min

data class DiffCore(
    val entries: List<DiffEntry>,
    val missingPassiveIds: List<Int>,
    val gearScores: List<GearScoreDto>
)

 object DiffService {

    private val modTagRegex = Regex("\\{[^}]*\\}")
    private val numRegex = Regex("-?\\d+(?:\\.\\d+)?")    
 
    fun diff(current: PobNormalized, target: PobNormalized): DiffCore {
         val entries = mutableListOf<DiffEntry>()
 
        // ---------- Камни ----------
        val curGems = current.gems.associateBy { it.name.lowercase() }
        for (g in target.gems) {
            val c = curGems[g.name.lowercase()]
            when {
                c == null ->
                    entries += DiffEntry("gem", "В таргете есть '${g.name}' ${g.level}/${g.quality} — в текущем билде отсутствует")
                c.level != g.level || c.quality != g.quality ->
                    entries += DiffEntry("gem", "'${g.name}': таргет ${g.level}/${g.quality}, текущий ${c.level}/${c.quality}")
            }
        }

        // ---------- Пассивки ----------
        val curPassives = current.passiveNodeIds.toSet()
        val missing = target.passiveNodeIds.filter { it !in curPassives }

        // ---------- Шмот: односторонний скоринг current против таргета (без Swap) ----------
        val gearScores = mutableListOf<GearScoreDto>()
        for ((slot, tName) in target.gear) {
            if ("Swap" in slot) continue
            val tItem = target.items.firstOrNull { it.name == tName } ?: continue
            val cName = current.gear[slot]
            val cItem = cName?.let { cn -> current.items.firstOrNull { it.name == cn } }
            val (score, missingMods) = when {
                cItem == null -> 0 to tItem.mods
                // разные уники в слоте — не mod-diff, а вердикт «заменить»
                tItem.rarity == "UNIQUE" && cItem.rarity == "UNIQUE"
                    && !cItem.name.equals(tItem.name, true) ->
                    0 to listOf("Заменить на: ${tItem.name} (${tItem.base})")
                else -> scoreItems(cItem, tItem)
            }
            gearScores += GearScoreDto(slot, cName, tName, score, missingMods)
        }

        // ---------- Конфиг ----------
        for ((k, v) in target.config) {
            val c = current.config[k]
            when {
                c == null -> entries += DiffEntry("config", "Конфиг '$k' не задан в текущем (таргет: $v)")
                c != v -> entries += DiffEntry("config", "Конфиг '$k': таргет $v, текущий $c")
            }
        }

        // ---------- Класс ----------
        if (target.className != null && target.className != current.className)
            entries += DiffEntry("class", "Класс: таргет ${target.className}, текущий ${current.className}")
        if (target.ascendancy != null && target.ascendancy != current.ascendancy)
            entries += DiffEntry("class", "Асценденси: таргет ${target.ascendancy}, текущий ${current.ascendancy}")

        return DiffCore(entries, missing, gearScores)
    }
    /** 0..100: покрытие модов таргета × близость роллов. */
    private fun scoreItems(c: ItemDto, t: ItemDto): Pair<Int, List<String>> {
        // Одинаковый УНИК = идентичные моды; рарки с одинаковым именем сравниваем по роллам
        if (t.rarity == "UNIQUE" && c.name.equals(t.name, ignoreCase = true)) return 100 to emptyList()
        if (t.mods.isEmpty()) return 100 to emptyList()
        val cByTpl = c.mods.associateBy { modTemplate(it) }
        var matched = 0
        var valueSum = 0.0
        val missing = mutableListOf<String>()
        for (tm in t.mods) {
            val cm = cByTpl[modTemplate(tm)]
            if (cm == null) { missing += tm; continue }
            matched++
            val tv = modValue(tm); val cv = modValue(cm)
            valueSum += if (tv != null && cv != null && tv != 0.0) min(abs(cv) / abs(tv), 1.0) else 1.0
        }
        val coverage = matched.toDouble() / t.mods.size
        val valueRatio = if (matched > 0) valueSum / matched else 0.0
        return (coverage * valueRatio * 100).toInt() to missing
    }

    private fun modTemplate(line: String): String =
        numRegex.replace(modTagRegex.replace(line, ""), "#").trim()

    private fun modValue(line: String): Double? =
        numRegex.find(line)?.value?.toDoubleOrNull()    
 }