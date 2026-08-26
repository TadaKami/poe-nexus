package com.poenexus.pob

import com.poenexus.http.HttpError
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node as W3cNode
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.Inflater
import javax.xml.parsers.DocumentBuilderFactory
import com.poenexus.ws.Events

class PobService(private val vertx: Vertx, private val pool: PgPool, private val tree: TreeDataService) {

    private val client = WebClient.create(vertx)

    suspend fun save(userId: String, url: String, scope: String): PobBuildDto {
        if (scope !in setOf("current", "target"))
            throw HttpError(400, "bad_scope", "scope must be 'current' or 'target'")

        val raw = resolveRaw(url)
        val hash = sha256(raw)
        val normalized = withContext(Dispatchers.IO) { parse(raw) }

        val row = pool.preparedQuery(
            """INSERT INTO pob_builds (user_id, scope, pastebin_url, version_hash, parsed_data)
               VALUES ($1, $2, $3, $4, $5::jsonb)
               ON CONFLICT (user_id, scope) DO UPDATE SET
                 pastebin_url = EXCLUDED.pastebin_url,
                 version_hash = EXCLUDED.version_hash,
                 parsed_data = EXCLUDED.parsed_data,
                 updated_at = now()
               RETURNING scope, pastebin_url, version_hash, parsed_data"""
        ).execute(
            Tuple.of(UUID.fromString(userId), scope, url, hash, JsonObject.mapFrom(normalized).encode())
        ).await().first()
        Events.user(vertx, userId, "pob.updated", JsonObject().put("scope", scope))
        return rowToDto(row)
    }

    suspend fun get(userId: String): List<PobBuildDto> =
        pool.preparedQuery(
            "SELECT scope, pastebin_url, version_hash, parsed_data FROM pob_builds WHERE user_id = $1"
        ).execute(Tuple.of(UUID.fromString(userId))).await().map { rowToDto(it) }

    suspend fun diff(userId: String): DiffReport {
        val builds = get(userId).associateBy { it.scope }
        val current = builds["current"] ?: throw HttpError(409, "no_current", "Current PoB not loaded")
        val target = builds["target"] ?: throw HttpError(409, "no_target", "Target PoB not loaded")
        val core = withContext(Dispatchers.IO) { DiffService.diff(current.parsed, target.parsed) }
        val version = target.parsed.treeVersion ?: current.parsed.treeVersion ?: "3_29"
        val missingPassives = tree.resolve(version, core.missingPassiveIds)
        val levelGap = maxOf(0, (target.parsed.level ?: 0) - (current.parsed.level ?: 0))
        return DiffReport(core.entries, missingPassives, core.missingPassiveIds, core.gearScores, levelGap)
    }

    suspend fun treePayload(version: String): TreePayload = tree.payload(version)

    // ---------- internals ----------

    /** Источник: pobb.in / pastebin / сырой PoB-код. */
    private suspend fun resolveRaw(source: String): String {
        val s = source.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) return s
        return fetchRaw(normalizeUrl(s))
    }

    private fun normalizeUrl(url: String): String = when {
        "pobb.in/" in url ->
            if (url.endsWith("/raw") || "/raw/" in url) url else url.trimEnd('/') + "/raw"
        "pastebin.com/" in url ->
            url.replace("pastebin.com/raw/", "pastebin.com/")
                .replace("pastebin.com/", "pastebin.com/raw/")
        else -> url
    }

    private suspend fun fetchRaw(url: String): String {
        val resp = client.getAbs(url).send().await()
        val body = resp.bodyAsString()
        if (resp.statusCode() != 200 || body.isNullOrBlank())
            throw HttpError(422, "pob_fetch_failed", "Cannot download PoB code")
        return body
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    /** PoB-код = base64(zlib(XML)). Алфавит может быть standard или URL-safe. */
    private fun parse(raw: String): PobNormalized {
        val std = raw.trim().replace('-', '+').replace('_', '/')
        val compressed = Base64.getMimeDecoder().decode(std)
        val xml = inflate(compressed)

        val dbf = DocumentBuilderFactory.newInstance()
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) // XXE-защита
        val doc = dbf.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = doc.documentElement

        val build = children(root).firstOrNull { it.tagName == "Build" }
        // ---------- PlayerStat (DPS/ES/резисты) ----------
        val stats = mutableMapOf<String, Double>()
        val statElems = root.getElementsByTagName("PlayerStat")
        for (i in 0 until statElems.length) {
            val e = statElems.item(i) as Element
            val v = e.getAttribute("value").toDoubleOrNull() ?: continue
            stats[e.getAttribute("stat")] = v
        }

        // ---------- Камни: <Gem> внутри <Skill slot=...> ----------
        val gems = mutableListOf<GemDto>()
        val gemElems = root.getElementsByTagName("Gem")
        for (i in 0 until gemElems.length) {
            val e = gemElems.item(i) as Element
            val parent = e.parentNode as? Element
            gems += GemDto(
                name = e.getAttribute("nameSpec").ifBlank { e.getAttribute("skillId") },
                skillId = e.getAttribute("skillId").ifBlank { null },
                level = e.getAttribute("level").toIntOrNull() ?: 1,
                quality = e.getAttribute("quality").toIntOrNull() ?: 0,
                enabled = e.getAttribute("enabled") != "false",
                slot = parent?.takeIf { it.tagName == "Skill" }
                    ?.getAttribute("slot")?.ifBlank { null }
            )
        }

        // ---------- Пассивки: Spec@nodes (comma-separated) ----------
        val spec = root.getElementsByTagName("Spec").item(0) as? Element
        val treeVersion = spec?.getAttribute("treeVersion")?.ifBlank { null }
        val passiveIds = spec?.getAttribute("nodes")
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

        // ---------- Тату / ранеграфты ----------
        val overrides = mutableListOf<String>()
        val ovElems = root.getElementsByTagName("Override")
        for (i in 0 until ovElems.length) {
            val dn = (ovElems.item(i) as Element).getAttribute("dn")
            if (dn.isNotBlank()) overrides += dn
        }

        // ---------- Предметы: сырой текст внутри <Item> ----------
        val items = mutableListOf<ItemDto>()
        val itemElems = root.getElementsByTagName("Item")
        for (i in 0 until itemElems.length) {
            val e = itemElems.item(i) as Element
            val id = e.getAttribute("id").toIntOrNull() ?: continue
            val lines = itemTextLines(e)
            if (lines.size < 3 || !lines[0].startsWith("Rarity:")) continue
            val mods = lines.drop(3).filter { !isMetaLine(it) }
            items += ItemDto(id, lines[0].removePrefix("Rarity:").trim(), lines[1], lines[2], mods)
        }

        // ---------- Экипировка по слотам (активный ItemSet) ----------
        val itemsEl = children(root).firstOrNull { it.tagName == "Items" }
        val activeSet = itemsEl?.getAttribute("activeItemSet") ?: "1"
        val byId = items.associateBy { it.id }
        val gear = mutableMapOf<String, String>()
        val setElems = root.getElementsByTagName("ItemSet")
        for (i in 0 until setElems.length) {
            val s = setElems.item(i) as Element
            if (s.getAttribute("id") != activeSet) continue
            val nodes = s.childNodes
            for (j in 0 until nodes.length) {
                val sl = nodes.item(j) as? Element ?: continue
                if (sl.tagName != "Slot") continue
                val itemId = sl.getAttribute("itemId").toIntOrNull() ?: 0
                if (itemId == 0) continue
                val name = byId[itemId]?.name ?: continue
                gear[sl.getAttribute("name")] = name
            }
        }

        // ---------- Конфиг: только активный ConfigSet ----------
        val config = mutableMapOf<String, String>()
        val configEl = children(root).firstOrNull { it.tagName == "Config" }
        val activeCfg = configEl?.getAttribute("activeConfigSet") ?: "1"
        val cfgSet = configEl?.let { c ->
            children(c).firstOrNull { it.tagName == "ConfigSet" && it.getAttribute("id") == activeCfg }
        }
        cfgSet?.let { set ->
            for (inp in children(set).filter { it.tagName == "Input" }) {
                val name = inp.getAttribute("name")
                if (name.isBlank()) continue
                config[name] = inp.getAttribute("number").ifBlank {
                    inp.getAttribute("string").ifBlank {
                        inp.getAttribute("boolean").ifBlank { "1" }
                    }
                }
            }
        }

        return PobNormalized(
            level = build?.getAttribute("level")?.toIntOrNull(),
            className = build?.getAttribute("className")?.ifBlank { null },
            ascendancy = build?.getAttribute("ascendClassName")?.ifBlank { null },
            gems = gems.filter { it.enabled },
            passiveNodeIds = passiveIds,
            treeVersion = treeVersion,
            overrides = overrides,
            items = items,
            gear = gear,
            config = config,
            stats = stats
        )
    }
    /** Сервисные строки предмета — не моды. */
    private val metaPrefixes = listOf(
        "Unique ID:", "Item Level:", "LevelReq:", "Implicits:", "Sockets:", "Quality:",
        "Energy Shield:", "Evasion:", "Armour:", "Intangibility:", "Catalyst",
        "Cluster Jewel", "Limited to:", "EnergyShieldBasePercentile", "EvasionBasePercentile",
        "ArmourBasePercentile", "Item Class:"
    )
    private fun isMetaLine(l: String) = metaPrefixes.any { l.startsWith(it) }

    /** Текстовые строки предмета (Rarity / имя / база / моды). */
    private fun itemTextLines(e: Element): List<String> {
        val sb = StringBuilder()
        val nodes = e.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == org.w3c.dom.Node.TEXT_NODE || n.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE)
                sb.append(n.nodeValue)
        }
        return sb.toString().split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }    

    private fun inflate(data: ByteArray): String {
        val inflater = Inflater()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        inflater.setInput(data)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toString(Charsets.UTF_8)
    }

    private fun children(e: Element): List<Element> {
        val list = e.childNodes
        val res = mutableListOf<Element>()
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n.nodeType == W3cNode.ELEMENT_NODE) res += n as Element
        }
        return res
    }

    private fun rowToDto(row: Row): PobBuildDto =
        PobBuildDto(
            scope = row.getString("scope"),
            pastebinUrl = row.getString("pastebin_url"),
            versionHash = row.getString("version_hash"),
            parsed = jsonToNormalized(JsonObject(row.getString("parsed_data")))
        )

    /** Ручной маппинг: не зависим от Jackson-KotlinModule в vertx-кодеке. */
    private fun jsonToNormalized(j: JsonObject): PobNormalized {
        val gearJson = j.getJsonObject("gear") ?: JsonObject()
        val configJson = j.getJsonObject("config") ?: JsonObject()
        val statsJson = j.getJsonObject("stats") ?: JsonObject()
        return PobNormalized(
            level = j.getInteger("level"),
            className = j.getString("className"),
            ascendancy = j.getString("ascendancy"),
            gems = j.getJsonArray("gems")?.map { o ->
                val g = o as JsonObject
                GemDto(
                    name = g.getString("name") ?: "",
                    skillId = g.getString("skillId"),
                    level = g.getInteger("level") ?: 1,
                    quality = g.getInteger("quality") ?: 0,
                    enabled = g.getBoolean("enabled") ?: true,
                    slot = g.getString("slot")
                )
            } ?: emptyList(),
            passiveNodeIds = j.getJsonArray("passiveNodeIds")?.map { (it as Number).toInt() }
                ?: emptyList(),
            treeVersion = j.getString("treeVersion"),
            overrides = j.getJsonArray("overrides")?.map { it.toString() } ?: emptyList(),
            items = j.getJsonArray("items")?.map { o ->
                val x = o as JsonObject
                ItemDto(
                    id = x.getInteger("id") ?: 0,
                    rarity = x.getString("rarity") ?: "",
                    name = x.getString("name") ?: "",
                    base = x.getString("base") ?: "",
                    mods = x.getJsonArray("mods")?.map { it.toString() } ?: emptyList()
                )
            } ?: emptyList(),
            gear = gearJson.fieldNames().associateWith { gearJson.getString(it) },
            config = configJson.fieldNames().associateWith { configJson.getString(it) },
            stats = statsJson.fieldNames().associateWith { statsJson.getDouble(it) ?: 0.0 }
        )
    }
}