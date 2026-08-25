package com.poenexus.auth

import com.poenexus.http.HttpError
import de.mkammerer.argon2.Argon2Factory
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthService(
    private val pool: PgPool,
    private val tokens: TokenService
) {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    data class Session(val response: AuthResponse, val rawRefresh: String)

    suspend fun register(email: String, password: String): Session {
        if (!emailRegex.matches(email)) throw HttpError(400, "invalid_email", "Invalid email format")
        if (password.length < 8) throw HttpError(400, "weak_password", "Password must be at least 8 characters")

        val existing = pool.preparedQuery("SELECT 1 FROM users WHERE lower(email) = $1")
            .execute(Tuple.of(email)).await()
        if (existing.size() > 0) throw HttpError(409, "email_taken", "Email already registered")

        val hash = hashPassword(password)
        val row = pool.preparedQuery(
            "INSERT INTO users (email, password_hash) VALUES ($1, $2) RETURNING id, email"
        ).execute(Tuple.of(email, hash)).await().first()

        return createSession(row.getValue("id").toString(), row.getString("email"))
    }

    suspend fun login(email: String, password: String): Session {
        val row = pool.preparedQuery(
            "SELECT id, email, password_hash FROM users WHERE lower(email) = $1"
        ).execute(Tuple.of(email)).await().firstOrNull()
            ?: throw HttpError(401, "bad_credentials", "Invalid email or password")

        if (!verifyPassword(row.getString("password_hash"), password))
            throw HttpError(401, "bad_credentials", "Invalid email or password")

        return createSession(row.getValue("id").toString(), row.getString("email"))
    }

    suspend fun refresh(rawRefresh: String): Session {
        val row = pool.preparedQuery(
            """SELECT r.id AS rid, u.id, u.email
               FROM refresh_tokens r JOIN users u ON u.id = r.user_id
               WHERE r.token_hash = $1 AND r.revoked_at IS NULL AND r.expires_at > now()"""
        ).execute(Tuple.of(tokens.sha256hex(rawRefresh))).await().firstOrNull()
            ?: throw HttpError(401, "invalid_refresh", "Session expired")

        // Ротация: старый токен гасим
        pool.preparedQuery("UPDATE refresh_tokens SET revoked_at = now() WHERE id = $1")
            .execute(Tuple.of(row.getValue("rid"))).await()

        return createSession(row.getValue("id").toString(), row.getString("email"))
    }

    suspend fun logout(rawRefresh: String) {
        pool.preparedQuery("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = $1")
            .execute(Tuple.of(tokens.sha256hex(rawRefresh))).await()
    }

    // ---------- internals ----------

    private suspend fun createSession(userId: String, email: String): Session {
        val access = tokens.issueAccessToken(userId, email)
        val raw = tokens.newRefreshToken()
        pool.preparedQuery(
            """INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
               VALUES ($1, $2, now() + interval '30 days')"""
        ).execute(Tuple.of(UUID.fromString(userId), tokens.sha256hex(raw))).await()
        return Session(AuthResponse(access, UserDto(userId, email)), raw)
    }

    // Argon2 — блокирующая либа, только в IO-потоке
    private suspend fun hashPassword(password: String): String = withContext(Dispatchers.IO) {
        argon2.hash(3, 64 * 1024, 1, password.toCharArray())
    }

    private suspend fun verifyPassword(hash: String, password: String): Boolean = withContext(Dispatchers.IO) {
        argon2.verify(hash, password.toCharArray())
    }
}