package com.poenexus.http

import com.poenexus.auth.AuthMiddleware
import com.poenexus.auth.AuthRoutes
import com.poenexus.auth.AuthService
import com.poenexus.auth.TokenService
import com.poenexus.config.AppConfig
import com.poenexus.infra.Db
import com.poenexus.nexus.NexusRoutes
import com.poenexus.nexus.NexusService
import com.poenexus.pob.PobRoutes
import com.poenexus.pob.PobService
import com.poenexus.pob.TreeDataService
import com.poenexus.ws.WsHandler
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import com.poenexus.nexus.SynergyService
import com.poenexus.atlas.AtlasRoutes
import com.poenexus.atlas.AtlasService
import com.poenexus.atlas.AtlasTreeDataService

class HttpServerVerticle : CoroutineVerticle() {

    override suspend fun start() {
        val cfg = AppConfig.load()
        val pool = Db.pool(vertx, cfg)
        val tokenService = TokenService(cfg.jwtSecret, cfg.accessTtlMinutes)
        val authService = AuthService(pool, tokenService)
        val nexusService = NexusService(vertx, pool)
        val synergyService = SynergyService(pool)
        val pobService = PobService(vertx, pool, TreeDataService(vertx))
        val authMiddleware = AuthMiddleware(tokenService)
        val wsHandler = WsHandler(vertx, tokenService)
        val atlasTreeService = AtlasTreeDataService(vertx)
        val atlasService = AtlasService(vertx, pool, atlasTreeService)

        val router = Router.router(vertx)
        router.route().handler(LoggerHandler.create())
        router.route().handler(BodyHandler.create())

        AuthRoutes(vertx, authService, tokenService).mount(router)
        NexusRoutes(vertx, nexusService, synergyService, authMiddleware).mount(router)
        PobRoutes(vertx, pobService, authMiddleware).mount(router)
        AtlasRoutes(vertx, atlasService, authMiddleware).mount(router)

        vertx.createHttpServer()
            .requestHandler(router)
            .webSocketHandler(wsHandler::handle)
            .listen(cfg.port)
            .await()

        println("HTTP server listening on :${cfg.port}")
    }
}