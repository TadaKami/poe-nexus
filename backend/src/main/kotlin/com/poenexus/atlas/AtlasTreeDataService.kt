package com.poenexus.atlas

import com.poenexus.pob.TreeNodeDto
import com.poenexus.pob.TreePayload
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Данные атлас-дерева: официальный экспорт GGG, ТОЛЬКО лига (league.json). */
class AtlasTreeDataService(private val vertx: Vertx) {

    private val client = WebClient.create(vertx)
    @Volatile private var cached: Map<String, JsonObject>? = null

    suspend fun payload(): TreePayload {
        val map = cached ?: load().also { cached = it }
        val nodes = mutableListOf<TreeNodeDto>()
        val ids = HashSet<Int>()
        for ((key, n) in map) {
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

    suspend fun nodeIds(): Set<Int> {
        val map = cached ?: load().also { cached = it }
        return map.keys.mapNotNull { it.toIntOrNull() }.toSet()
    }

    private suspend fun load(): Map<String, JsonObject> = withContext(Dispatchers.IO) {
        val url = "https://raw.githubusercontent.com/grindinggear/atlastree-export/master/league.json"
        try {
            val resp = client.getAbs(url).putHeader("User-Agent", "PoENexus-dev").send().await()
            println("[AtlasTree] $url -> ${resp.statusCode()}")
            if (resp.statusCode() != 200) return@withContext emptyMap()
            val root = JsonObject(resp.bodyAsString())
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
            println("[AtlasTree] normalize: ${out.size} nodes")
            out
        } catch (e: Exception) {
            println("[AtlasTree] ошибка: $e")
            emptyMap()
        }
    }
}