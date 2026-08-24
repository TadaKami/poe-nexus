package com.poenexus

import io.vertx.core.Vertx

/**
 * Точка входа backend-приложения.
 *
 * Архитектура: модульный монолит на Vert.x.
 * Каждый функциональный блок — отдельный Verticle:
 *   - AuthVerticle      (Модуль 1: авторизация)   <- следующий шаг
 *   - NexusApiVerticle  (Модуль 1: группы)
 *   - WebSocketVerticle (Real-time канал с Vue)
 *   - PobVerticle       (Модуль 3: PoB парсер)
 *   - AtlasVerticle     (Модуль 4: карта Атласа)
 */
fun main() {
    val vertx = Vertx.vertx()

    // Деплой вертиклов по мере разработки модулей:
    // vertx.deployVerticle(AuthVerticle())
    // vertx.deployVerticle(NexusApiVerticle())
    // vertx.deployVerticle(WebSocketVerticle())

    println("PoE Nexus backend: Vert.x инициализирован")
}