package com.poenexus.atlas

import com.poenexus.auth.AuthMiddleware
import com.poenexus.http.HttpError
import com.poenexus.http.RouteModule
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.core.json.JsonObject

class AtlasRoutes(
    vertx: Vertx,
    private val service: AtlasService,
    private val authMiddleware: AuthMiddleware
) : RouteModule(vertx) {

    fun mount(router: Router) {
        val auth = authMiddleware::handle
        router.get("/api/atlas/tree").handler(auth).handler(::handleTree)
        router.post("/api/atlas").handler(auth).handler(::handleSave)
        router.get("/api/atlas").handler(auth).handler(::handleGet)
        router.get("/api/nexus/:id/atlas").handler(auth).handler(::handleNexus)
        router.get("/api/nexus/:id/atlas/alloc").handler(auth).handler(::handleGetAlloc)
        router.post("/api/nexus/:id/atlas/alloc").handler(auth).handler(::handleToggleAlloc)
    }

    private fun handleTree(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.response().putHeader("Cache-Control", "public, max-age=3600")
        ctx.json(service.treePayload())
    }

    private fun handleSave(ctx: RoutingContext) = launchSafe(ctx) {
        val url = ctx.body().asJsonObject()?.getString("url")
            ?: throw HttpError(400, "bad_request", "url required")
        ctx.json(service.save(ctx.userId(), url))
    }

    private fun handleGet(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.get(ctx.userId()) ?: JsonObject())
    }

    private fun handleNexus(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.nexusAtlases(ctx.pathParam("id")))
    }

    private fun handleGetAlloc(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.getAlloc(ctx.pathParam("id")))
    }

    private fun handleToggleAlloc(ctx: RoutingContext) = launchSafe(ctx) {
        val json = ctx.body().asJsonObject() ?: throw HttpError(400, "bad_request", "JSON body required")
        val add = json.getJsonArray("add")?.map { (it as Number).toInt() } ?: emptyList()
        val remove = json.getJsonArray("remove")?.map { (it as Number).toInt() } ?: emptyList()
        ctx.json(service.toggle(ctx.pathParam("id"), ctx.userId(), add, remove))
    }
}