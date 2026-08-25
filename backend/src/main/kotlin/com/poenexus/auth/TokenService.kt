package com.poenexus.auth

import io.vertx.core.json.JsonObject
import io.vertx.core.http.Cookie
import io.vertx.core.http.CookieSameSite
import io.vertx.ext.web.RoutingContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TokenService(private val secret: String, private val accessTtlMinutes: Long) {

    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val random = SecureRandom()

    // ---------- Access JWT (HS256) ----------

    fun issueAccessToken(userId: String, email: String): String {
        val header = b64.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val now = System.currentTimeMillis() / 1000
        val payload = b64.encodeToString(
            JsonObject()
                .put("sub", userId)
                .put("email", email)
                .put("iat", now)
                .put("exp", now + accessTtlMinutes * 60)
                .encode().toByteArray()
        )
        return "$header.$payload.${sign("$header.$payload")}"
    }

    fun verifyAccessToken(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val expected = sign("${parts[0]}.${parts[1]}")
        // constant-time сравнение подписи
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[2].toByteArray())) return null
        return try {
            val payload = JsonObject(String(Base64.getUrlDecoder().decode(parts[1])))
            val exp = payload.getLong("exp") ?: return null
            if (exp < System.currentTimeMillis() / 1000) null else payload
        } catch (e: Exception) {
            null
        }
    }

    private fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return b64.encodeToString(mac.doFinal(data.toByteArray()))
    }

    // ---------- Refresh: opaque + SHA-256 для БД ----------

    fun newRefreshToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return b64.encodeToString(bytes)
    }

    fun sha256hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ---------- Cookie ----------

    fun setRefreshCookie(ctx: RoutingContext, raw: String) {
        ctx.response().addCookie(
            Cookie.cookie("poe_refresh", raw)
                .setHttpOnly(true)
                .setPath("/api/auth")
                .setMaxAge(30L * 24 * 3600)
                .setSameSite(CookieSameSite.STRICT)
            // .setSecure(true) — включить в проде за HTTPS
        )
    }

    fun readRefreshCookie(ctx: RoutingContext): String? =
        ctx.request().getCookie("poe_refresh")?.value

    fun clearRefreshCookie(ctx: RoutingContext) {
        ctx.response().addCookie(
            Cookie.cookie("poe_refresh", "")
                .setHttpOnly(true)
                .setPath("/api/auth")
                .setMaxAge(0)
        )
    }
}