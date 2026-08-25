package com.poenexus.nexus

import com.poenexus.auth.AuthMiddleware
import com.poenexus.http.HttpError
import com.poenexus.http.RouteModule
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

class NexusRoutes(
    vertx: Vertx,
    private val service: NexusService,
    private val authMiddleware: AuthMiddleware
) : RouteModule(vertx) {

    fun mount(router: Router) {
        val auth = authMiddleware::handle
        router.get("/api/nexus").handler(auth).handler(::handleList)
        router.post("/api/nexus").handler(auth).handler(::handleCreate)
        router.get("/api/nexus/:id").handler(auth).handler(::handleDetails)
        router.post("/api/nexus/:id/invite").handler(auth).handler(::handleInvite)
        router.post("/api/nexus/join").handler(auth).handler(::handleJoin)
        router.patch("/api/nexus/:id/members/:userId").handler(auth).handler(::handleRoleChange)
        router.delete("/api/nexus/:id/members/:userId").handler(auth).handler(::handleKick)
        router.delete("/api/nexus/:id").handler(auth).handler(::handleDelete)
    }

    private fun handleList(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.list(ctx.userId()))
    }

    private fun handleCreate(ctx: RoutingContext) = launchSafe(ctx) {
        val json = ctx.body().asJsonObject() ?: throw HttpError(400, "bad_request", "JSON body required")
        ctx.response().setStatusCode(201)
        ctx.json(service.create(ctx.userId(), json.getString("name") ?: "", json.getString("description")))
    }

    private fun handleDetails(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.json(service.details(ctx.userId(), ctx.pathParam("id")))
    }

    private fun handleInvite(ctx: RoutingContext) = launchSafe(ctx) {
        ctx.response().setStatusCode(201)
        ctx.json(service.invite(ctx.userId(), ctx.pathParam("id")))
    }

    private fun handleJoin(ctx: RoutingContext) = launchSafe(ctx) {
        val code = ctx.body().asJsonObject()?.getString("code")
            ?: throw HttpError(400, "bad_request", "code required")
        ctx.json(service.join(ctx.userId(), code))
    }

    private fun handleRoleChange(ctx: RoutingContext) = launchSafe(ctx) {
        val role = ctx.body().asJsonObject()?.getString("role")
            ?: throw HttpError(400, "bad_request", "role required")
        service.changeRole(ctx.userId(), ctx.pathParam("id"), ctx.pathParam("userId"), role)
        ctx.response().setStatusCode(204).end()
    }

    private fun handleKick(ctx: RoutingContext) = launchSafe(ctx) {
        service.kick(ctx.userId(), ctx.pathParam("id"), ctx.pathParam("userId"))
        ctx.response().setStatusCode(204).end()
    }

    private fun handleDelete(ctx: RoutingContext) = launchSafe(ctx) {
        service.delete(ctx.userId(), ctx.pathParam("id"))
        ctx.response().setStatusCode(204).end()
    }
}