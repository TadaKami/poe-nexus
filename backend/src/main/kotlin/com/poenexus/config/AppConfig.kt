package com.poenexus.config

import io.vertx.core.json.JsonObject

data class AppConfig(
    val port: Int,
    val dbUrl: String,
    val poolSize: Int,
    val sslMode: String,
    val jwtSecret: String,
    val accessTtlMinutes: Long
) {
    companion object {
        fun load(): AppConfig {
            val stream = AppConfig::class.java.getResourceAsStream("/config.json")
                ?: error("config.json not found on classpath")
            val json = JsonObject(stream.readBytes().decodeToString())

            // Приоритет: env -> config.json
            val dbUrl = System.getenv("DATABASE_URL")
                ?: json.getJsonObject("db").getString("url")

            val sslMode = System.getenv("PG_SSL_MODE")
                ?: json.getJsonObject("db").getString("sslMode", "REQUIRE")

            var jwtSecret = System.getenv("JWT_SECRET")
                ?: json.getJsonObject("jwt").getString("secret")
            if (jwtSecret.contains("\${")) {
                // плейсхолдер не подменён — dev-фолбэк
                jwtSecret = "dev_secret_change_me"
            }

            return AppConfig(
                port = json.getJsonObject("server").getInteger("port", 8080),
                dbUrl = dbUrl,
                poolSize = json.getJsonObject("db").getInteger("poolSize", 5),
                sslMode = sslMode,
                jwtSecret = jwtSecret,
                accessTtlMinutes = json.getJsonObject("jwt").getLong("accessTtlMinutes", 15)
            )
        }
    }
}