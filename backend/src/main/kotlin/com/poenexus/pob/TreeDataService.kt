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

    /** Конденсированное дерево для SVG-визуализации: ноды + рёбра. */
    suspend fun payload(version: String): TreePayload {
        val map = cache[version] ?: load(version).also { cache[version] = it }
        val nodes = mutableListOf<TreeNodeDto>()
        val ids = HashSet<Int>()
        for ((key, n) in map) {
            if (n.getString("ascendancyName") != null) continue // ascendancy-кластеры растягивают bounds
            val id = n.getInteger("id") ?: key.toIntOrNull() ?: continue
            val iconRaw = n.getString("icon") ?: ""
            nodes += TreeNodeDto(
                id = id,
                x = (n.getValue("x") as? Number)?.toDouble() ?: 0.0,
                y = (n.getValue("y") as? Number)?.toDouble() ?: 0.0,
                kind = when {
                    n.getBoolean("ks") == true -> "keystone"
                    n.getBoolean("notable") == true -> "notable"
                    else -> "normal"
                },
                icon = when {
                    iconRaw.isBlank() -> null
                    iconRaw.startsWith("http") -> iconRaw
                    else -> "https://web.poecdn.com/$iconRaw"
                },
                name = n.getString("name")
            )
            ids += id
        }
        val edges = mutableListOf<List<Int>>()
        val seen = HashSet<Long>()
        for ((key, n) in map) {
            val id = n.getInteger("id") ?: key.toIntOrNull() ?: continue
            val out = n.getJsonArray("out") ?: continue
            for (i in 0 until out.size()) {
                val other = (out.getValue(i) as? Number)?.toInt() ?: continue
                if (other !in ids) continue
                val a = minOf(id, other); val b = maxOf(id, other)
                val ek = a.toLong() shl 32 or b.toLong()
                if (seen.add(ek)) edges += listOf(a, b)
            }
        }
        return TreePayload(nodes, edges)
    }    

    private suspend fun load(version: String): Map<String, JsonObject> = withContext(Dispatchers.IO) {
        // Официальный экспорт GGG: текущая лига, без ruthless
        val sources = listOf(
            "https://raw.githubusercontent.com/grindinggear/skilltree-export/master/data.json"
        )
        for (url in sources) {
            try {
                val resp = client.getAbs(url).putHeader("User-Agent", "PoENexus-dev").send().await()
                println("[TreeData] $url -> ${resp.statusCode()}")
                if (resp.statusCode() != 200) continue
                val root = JsonObject(resp.bodyAsString())
                if (root == null) { println("[TreeData] LuaJson вернул null для $url"); continue }
                val map = normalize(root)
                println("[TreeData] normalize: ${map.size} nodes из $url")
                if (map.isNotEmpty()) return@withContext map
            } catch (e: Exception) {
                // источник недоступен — пробуем следующий
                println("[TreeData] ошибка для $url: $e")
            }
        }
        emptyMap()
    }

    /** Сливаем nodes (детали) и groups (координаты). */
    private fun normalize(root: JsonObject): Map<String, JsonObject> {
        val out = HashMap<String, JsonObject>()
        root.getJsonObject("nodes")?.let { nodes ->
            for (key in nodes.fieldNames()) {
                val node = nodes.getJsonObject(key) ?: continue
                if (node.getValue("id") == null) node.put("id", key.toIntOrNull() ?: -1)
                out[key] = node
            }
        }
        root.getJsonObject("groups")?.let { groups ->
            for (gk in groups.fieldNames()) {
                val g = groups.getJsonObject(gk) ?: continue
                val gx = (g.getValue("x") as? Number)?.toDouble() ?: continue
                val gy = (g.getValue("y") as? Number)?.toDouble() ?: continue
                val idsArr = g.getJsonArray("nodes") ?: continue
                val count = idsArr.size()
                for (idx in 0 until count) {
                    val id = idsArr.getString(idx) ?: continue
                    val node = out[id] ?: JsonObject().put("id", id.toIntOrNull() ?: -1).also { out[id] = it }
                    if (node.getValue("x") == null) {
                        // MVP-аппроксимация: ноды группы по окружности вокруг центра
                        val angle = 2.0 * Math.PI * idx / count
                        val r = if (count == 1) 0.0 else 130.0
                        node.put("x", gx + r * Math.cos(angle))
                        node.put("y", gy + r * Math.sin(angle))
                    }
                }
            }
        }
        return out
    }
}