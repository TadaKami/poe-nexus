package com.poenexus.pob

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Резолвит ID пассивных нод в имена/эффекты/иконки.
 * Источник: TreeData из репозитория PoB (версия = Spec@treeVersion),
 * фолбэк — официальный data.json GGG. Кэш в памяти per-version.
 */
class TreeDataService(private val vertx: Vertx) {

    private val client = WebClient.create(vertx)
    private val cache = ConcurrentHashMap<String, Map<String, JsonObject>>()

    suspend fun resolve(version: String, ids: List<Int>): List<PassiveNodeInfo> {
        if (ids.isEmpty()) return emptyList()
        val map = cache[version] ?: load(version).also { cache[version] = it }
        return ids.mapNotNull { id ->
            val n = map[id.toString()] ?: return@mapNotNull null
            val iconRaw = n.getString("icon") ?: ""
            PassiveNodeInfo(
                id = id,
                name = n.getString("name") ?: "Node $id",
                effects = n.getJsonArray("sd")?.map { it.toString() } ?: emptyList(),
                icon = when {
                    iconRaw.isBlank() -> null
                    iconRaw.startsWith("http") -> iconRaw
                    else -> "https://web.poecdn.com/$iconRaw"
                },
                keystone = n.getBoolean("ks") ?: false,
                notable = n.getBoolean("notable") ?: false
            )
        }
    }

    private suspend fun load(version: String): Map<String, JsonObject> = withContext(Dispatchers.IO) {
        val sources = listOf(
            "https://raw.githubusercontent.com/PathOfBuildingCommunity/PathOfBuilding/dev/src/TreeData/$version/data.json",
            "https://raw.githubusercontent.com/PathOfBuildingCommunity/PathOfBuilding/master/src/TreeData/$version/data.json",
            "https://www.pathofexile.com/passive-skill-tree/data.json"
        )
        for (url in sources) {
            try {
                val resp = client.getAbs(url).putHeader("User-Agent", "PoENexus-dev").send().await()
                if (resp.statusCode() != 200) continue
                val nodes = JsonObject(resp.bodyAsString()).getJsonObject("nodes") ?: continue
                val map = HashMap<String, JsonObject>(nodes.size())
                for (key in nodes.fieldNames()) {
                    val n = nodes.getJsonObject(key) ?: continue
                    if (n.getString("name") != null) map[key] = n
                }
                if (map.isNotEmpty()) return@withContext map
            } catch (e: Exception) {
                // источник недоступен — пробуем следующий
            }
        }
        emptyMap()
    }
}