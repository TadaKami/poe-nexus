package com.poenexus.auth

import com.poenexus.http.HttpError
import com.poenexus.http.RouteModule
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

class AuthRoutes(
    vertx: Vertx,
    private val auth: AuthService,
    private val tokens: TokenService
) : RouteModule(vertx) {

    fun mount(router: Router) {
        router.post("/api/auth/register").handler(::handleRegister)
        router.post("/api/auth/login").handler(::handleLogin)
        router.post("/api/auth/refresh").handler(::handleRefresh)
        router.post("/api/auth/logout").handler(::handleLogout)
    }

    private fun handleRegister(ctx: RoutingContext) = launchSafe(ctx) {
        val json = ctx.body().asJsonObject() ?: throw HttpError(400, "bad_request", "JSON body required")
        val email = (json.getString("email") ?: "").trim().lowercase()
        val password = json.getString("password") ?: ""
        val session = auth.register(email, password)
        tokens.setRefreshCookie(ctx, session.rawRefresh)
        ctx.response().setStatusCode(201)
        ctx.json(session.response)
    }

    private fun handleLogin(ctx: RoutingContext) = launchSafe(ctx) {
        val json = ctx.body().asJsonObject() ?: throw HttpError(400, "bad_request", "JSON body required")
        val email = (json.getString("email") ?: "").trim().lowercase()
        val password = json.getString("password") ?: ""
        val session = auth.login(email, password)
        tokens.setRefreshCookie(ctx, session.rawRefresh)
        ctx.json(session.response)
    }

    private fun handleRefresh(ctx: RoutingContext) = launchSafe(ctx) {
        val raw = tokens.readRefreshCookie(ctx)
            ?: throw HttpError(401, "invalid_refresh", "No refresh cookie")
        val session = auth.refresh(raw)
        tokens.setRefreshCookie(ctx, session.rawRefresh)
        ctx.json(session.response)
    }

    private fun handleLogout(ctx: RoutingContext) = launchSafe(ctx) {
        tokens.readRefreshCookie(ctx)?.let { auth.logout(it) }
        tokens.clearRefreshCookie(ctx)
        ctx.response().setStatusCode(204).end()
    }
}