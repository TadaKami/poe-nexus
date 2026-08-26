package com.poenexus.ws

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/** Конверт события и паблишер в Event Bus (адрес = user.{id}). */
object Events {

    fun user(vertx: Vertx, userId: String, type: String, payload: JsonObject = JsonObject()) {
        vertx.eventBus().publish("user.$userId", envelope(type, payload))
    }

    fun users(vertx: Vertx, ids: Collection<String>, type: String, payload: JsonObject = JsonObject()) {
        val env = envelope(type, payload)
        ids.forEach { vertx.eventBus().publish("user.$it", env) }
    }

    private fun envelope(type: String, payload: JsonObject) =
        JsonObject()
            .put("type", type)
            .put("ts", System.currentTimeMillis())
            .put("payload", payload)
}