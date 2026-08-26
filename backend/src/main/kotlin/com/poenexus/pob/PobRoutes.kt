package com.poenexus.pob

import com.poenexus.auth.AuthMiddleware
import com.poenexus.http.HttpError
import com.poenexus.http.RouteModule
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

class PobRoutes(
    vertx: Vertx,
    private val service: PobService,
    private val authMiddleware: AuthMiddleware
) : RouteModule(vertx) {

    fun mount(router: Router) {
        val auth = authMiddleware::handle
        router.post("/api/pob").handler(auth).handler(::handleSave)
        router.get("/api/pob").handler(auth).handler(::handleGet)
        router.post("/api/pob/diff").handler(auth).handler(::handleDiff)
        router.get("/api/pob/tree/:version").handler(auth).handler(::handleTree)
    }

    private fun handleSave(ctx: RoutingContext) = launchSafe(ctx) {
        val json = ctx.body().asJsonObject() ?: throw HttpError(400, "bad_request", "JSON body required")
        val url = json.getString("source") ?: json.getString("url")
            ?: throw HttpError(400, "bad_request", "source required")
        val scope = json.getString("scope") ?: "current"
        ctx.response().setStatusCode(201)
        ctx.json(service.save(ctx.userId(), url, scope))
    }

    private fun handleGet(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.get(ctx.userId()))
    }

    private fun handleDiff(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.diff(ctx.userId()))
    }

    private fun handleTree(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.treePayload(ctx.pathParam("version")))
    }    
}