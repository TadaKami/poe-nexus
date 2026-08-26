package com.poenexus.pob

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Прокси иконок: браузеры с адблоками режут прямые запросы
 * к poecdn/poewiki, а серверу — нет. Публичный эндпоинт (для <img>).
 */
class IconProxyService(private val vertx: Vertx) {

    private val client = WebClient.create(vertx)
    private val cache = ConcurrentHashMap<String, Pair<String, Buffer>>()
    private val allowed = setOf(
        "web.poecdn.com", "www.poewiki.net", "poewiki.net", "static.wikia.nocookie.net"
    )

    fun mount(router: Router) {
        router.get("/api/pob/icon").handler { ctx ->
            val url = ctx.queryParams().get("url")
            if (url == null) {
                ctx.response().setStatusCode(400).end()
                return@handler
            }                
            val host = try {
                java.net.URI(url).host
            } catch (e: Exception) {
                null
            }
            if (host !in allowed) {
                ctx.response().setStatusCode(403).end()
                return@handler
            }

            CoroutineScope(vertx.dispatcher()).launch {
                try {
                    val hit = cache[url]
                    if (hit != null) {
                        ctx.response()
                            .putHeader("Content-Type", hit.first)
                            .putHeader("Cache-Control", "public, max-age=86400")
                            .end(hit.second)
                        return@launch
                    }
                    val resp = client.getAbs(url).putHeader("User-Agent", "PoENexus-dev").send().await()
                    if (resp.statusCode() == 200 && resp.body() != null) {
                        val ct = resp.getHeader("Content-Type") ?: "image/png"
                        if (cache.size < 2000) cache[url] = ct to resp.body()
                        ctx.response()
                            .putHeader("Content-Type", ct)
                            .putHeader("Cache-Control", "public, max-age=86400")
                            .end(resp.body())
                    } else {
                        ctx.response().setStatusCode(502).end()
                    }
                } catch (e: Exception) {
                    if (!ctx.response().ended()) ctx.response().setStatusCode(502).end()
                }
            }
        }
    }
}