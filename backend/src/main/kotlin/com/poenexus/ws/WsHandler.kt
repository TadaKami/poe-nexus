package com.poenexus.ws

import com.poenexus.auth.TokenService
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.JsonObject

/**
 * WebSocket-хендшейк: JWT из ?token=, подписка сокета на user.{id},
 * heartbeat-пинг, очистка по close.
 */
class WsHandler(private val vertx: Vertx, private val tokens: TokenService) {

    fun handle(ws: ServerWebSocket) {
        val token = ws.query()
            ?.split('&')
            ?.firstOrNull { it.startsWith("token=") }
            ?.removePrefix("token=")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }

        val payload = token?.let { tokens.verifyAccessToken(it) }
        if (payload == null) {
            ws.reject(401)
            return
        }
        val userId = payload.getString("sub")
        ws.accept()

        // Статичная подписка: только личный адрес пользователя
        val consumer = vertx.eventBus().consumer<JsonObject>("user.$userId") { msg ->
            if (!ws.isClosed) ws.writeTextMessage(msg.body().encode())
        }

        val pingId = vertx.setPeriodic(25000) {
            if (!ws.isClosed) ws.writePing(Buffer.buffer())
        }

        ws.closeHandler {
            consumer.unregister()
            vertx.cancelTimer(pingId)
        }
        ws.exceptionHandler {
            consumer.unregister()
            vertx.cancelTimer(pingId)
            if (!ws.isClosed) ws.close()
        }

        ws.writeTextMessage(
            JsonObject().put("type", "hello").put("userId", userId)
                .put("ts", System.currentTimeMillis()).encode()
        )
    }
}