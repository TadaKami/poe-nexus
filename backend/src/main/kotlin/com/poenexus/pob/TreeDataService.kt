package com.poenexus.pob

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Данные пассивного дерева (официальный экспорт GGG, только лига).
 * Кэш: ноды + masteryEffects per-version.
 */
class TreeDataService(private val vertx: Vertx) {

    private data class RawTree(
        val nodes: Map<String, JsonObject>,
        val masteryEffects: Map<String, JsonObject>
    )

    private val client = WebClient.create(vertx)
    private val cache = ConcurrentHashMap<String, RawTree>()

    suspend fun resolve(version: String, ids: List<Int>): List<PassiveNodeInfo> {
        if (ids.isEmpty()) return emptyList()
        val raw = cache[version] ?: load(version).also { cache[version] = it }
        return ids.mapNotNull { id ->
            val n = raw.nodes[id.toString()] ?: return@mapNotNull null
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

    /** Текстовые моды взятых нод (sd) + выбранных mastery-эффектов. */
    suspend fun statLines(version: String, nodeIds: List<Int>, masteryEffectIds: List<Int>): List<String> {
        val raw = cache[version] ?: load(version).also { cache[version] = it }
        val lines = mutableListOf<String>()
        for (id in nodeIds) {
            raw.nodes[id.toString()]?.getJsonArray("sd")?.let { sd ->
                for (i in 0 until sd.size()) sd.getString(i)?.let { lines += it }
            }
        }
        for (eid in masteryEffectIds) {
            raw.masteryEffects[eid.toString()]?.getJsonArray("sd")?.let { sd ->
                for (i in 0 until sd.size()) sd.getString(i)?.let { lines += it }
            }
        }
        return lines
    }

    /** Конденсированное дерево для SVG: ноды + рёбра. */
    suspend fun payload(version: String): TreePayload {
        val raw = cache[version] ?: load(version).also { cache[version] = it }
        val nodes = mutableListOf<TreeNodeDto>()
        val ids = HashSet<Int>()
        for ((key, n) in raw.nodes) {
            if (n.getString("ascendancyName") != null) continue
            val id = n.getInteger("id") ?: key.toIntOrNull() ?: continue
            val iconRaw = n.getString("icon") ?: ""
            nodes += TreeNodeDto(
                id = id,
                x = (n.getValue("x") as? Number)?.toDouble() ?: 0.0,
                y = (n.getValue("y") as? Number)?.toDouble() ?: 0.0,
                kind = when {
                    n.getBoolean("ks") == true -> "keystone"
                    n.getBoolean("notable") == true -> "notable"
                    n.getBoolean("isMastery") == true || iconRaw.contains("Mastery") -> "mastery"
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
        for ((key, n) in raw.nodes) {
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

    private suspend fun load(version: String): RawTree = withContext(Dispatchers.IO) {
        val sources = listOf(
            "https://raw.githubusercontent.com/grindinggear/skilltree-export/master/data.json"
        )
        for (url in sources) {
            try {
                val resp = client.getAbs(url).putHeader("User-Agent", "PoENexus-dev").send().await()
                println("[TreeData] $url -> ${resp.statusCode()}")
                if (resp.statusCode() != 200) continue
                val root = JsonObject(resp.bodyAsString())
                val nodes = normalize(root)
                val me = mutableMapOf<String, JsonObject>()
                root.getJsonObject("masteryEffects")?.let { m ->
                    for (k in m.fieldNames()) m.getJsonObject(k)?.let { me[k] = it }
                }
                println("[TreeData] normalize: ${nodes.size} nodes, ${me.size} masteryEffects")
                if (nodes.isNotEmpty()) return@withContext RawTree(nodes, me)
            } catch (e: Exception) {
                println("[TreeData] ошибка для $url: $e")
            }
        }
        RawTree(emptyMap(), emptyMap())
    }

    /** Сливаем nodes (детали) и groups (координаты, раскладка по орбитам). */
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
                val orbits = g.getJsonArray("orbits")
                val gx = (g.getValue("x") as? Number)?.toDouble() ?: continue
                val gy = (g.getValue("y") as? Number)?.toDouble() ?: continue
                val idsArr = g.getJsonArray("nodes") ?: continue
                val count = idsArr.size()
                for (idx in 0 until count) {
                    val id = idsArr.getString(idx) ?: continue
                    val node = out[id] ?: JsonObject().put("id", id.toIntOrNull() ?: -1).also { out[id] = it }
                    if (node.getValue("x") == null) {
                        val orbit = if (orbits != null && orbits.size() > 0)
                            (orbits.getValue(minOf(idx, orbits.size() - 1)) as? Number)?.toInt() ?: 0 else 0
                        val angle = 2.0 * Math.PI * idx / count
                        val r = orbit * 80.0
                        node.put("x", gx + r * Math.cos(angle))
                        node.put("y", gy + r * Math.sin(angle))
                    }
                }
            }
        }
        return out
    }
}