package com.poenexus.pob
 
/** Промежуточный результат: текстовые расхождения + сырые ID пассивок. */
data class DiffCore(val entries: List<DiffEntry>, val missingPassiveIds: List<Int>)

 object DiffService {
 
    fun diff(current: PobNormalized, target: PobNormalized): DiffCore {
         val entries = mutableListOf<DiffEntry>()
 
        // ---------- Камни: отсутствующие и расхождения уровней/качества ----------
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
 
        // ---------- Пассивки: только собираем ID, резолв имён — в PobService ----------
         val curPassives = current.passiveNodeIds.toSet()
         val missing = target.passiveNodeIds.filter { it !in curPassives }
 
        // ---------- Экипировка по слотам ----------
         for ((slot, tName) in target.gear) {
             val cName = current.gear[slot]
             when {
                 cName == null ->
                     entries += DiffEntry("gear", "Слот '$slot': в таргете '$tName', в текущем пусто")
                 cName != tName ->
                     entries += DiffEntry("gear", "Слот '$slot': таргет '$tName', текущий '$cName'")
             }
         }
 
        // ---------- Моды не-уник предметов по слотам ----------
         for ((slot, tName) in target.gear) {
             val cName = current.gear[slot] ?: continue
             if (cName == tName) continue
             val tItem = target.items.firstOrNull { it.name == tName } ?: continue
             val cItem = current.items.firstOrNull { it.name == cName } ?: continue
             if (tItem.rarity == "UNIQUE") continue // уники сравниваются по имени
             val cMods = cItem.mods.toSet()
             val tMods = tItem.mods.toSet()
             for (m in tMods - cMods)
                 entries += DiffEntry("item", "Слот '$slot' (${tItem.name}): нет мода '$m'")
             for (m in cMods - tMods)
                 entries += DiffEntry("item", "Слот '$slot': лишний мод '$m' (в таргете нет)")
         }
 
        // ---------- Конфиг (ауры/проклятия/условия из вкладки Configuration) ----------
         for ((k, v) in target.config) {
             val c = current.config[k]
             when {
                 c == null -> entries += DiffEntry("config", "Конфиг '$k' не задан в текущем (таргет: $v)")
                 c != v -> entries += DiffEntry("config", "Конфиг '$k': таргет $v, текущий $c")
             }
         }
 
        // ---------- Класс/асценденси ----------
         if (target.className != null && target.className != current.className)
             entries += DiffEntry("class", "Класс: таргет ${target.className}, текущий ${current.className}")
         if (target.ascendancy != null && target.ascendancy != current.ascendancy)
             entries += DiffEntry("class", "Асценденси: таргет ${target.ascendancy}, текущий ${current.ascendancy}")
 
        return DiffCore(entries, missing)
     }
 }