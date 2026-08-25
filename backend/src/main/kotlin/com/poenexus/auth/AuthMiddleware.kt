package com.poenexus.auth

import io.vertx.ext.web.RoutingContext

/** Валидирует Bearer JWT и кладёт userId в контекст. */
class AuthMiddleware(private val tokens: TokenService) {

    fun handle(ctx: RoutingContext) {
        val header = ctx.request().getHeader("Authorization") ?: ""
        val payload = if (header.startsWith("Bearer ")) {
            tokens.verifyAccessToken(header.removePrefix("Bearer ").trim())
        } else null

        if (payload == null) {
            ctx.response().setStatusCode(401)
            ctx.json(ApiError("unauthorized", "Not authenticated"))
            return
        }
        ctx.put("userId", payload.getString("sub"))
        ctx.put("userEmail", payload.getString("email"))
        ctx.next()
    }
}