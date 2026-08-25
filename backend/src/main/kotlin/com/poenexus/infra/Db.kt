package com.poenexus.infra

import com.poenexus.config.AppConfig
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.pgclient.SslMode
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlConnection

object Db{
    fun pool(vertx: Vertx, cfg: AppConfig): PgPool = 
        PgPool.pool(
            vertx,
            PgConnectOptions.fromUri(cfg.dbUrl)
                .setSslMode(SslMode.REQUIRE)
                .setCachePreparedStatements(false),
            PoolOptions().setMaxSize(cfg.poolSize)
        )
    
    /** Транзакция на корутинах: begin -> block -> commit, при ошибке rollback. */
    suspend fun <T> PgPool.inTransaction(block: suspend (SqlConnection) -> T): T {
        val conn = connection.await()
        return try {
            val tx = conn.begin().await()
            try {
                val result = block(conn)
                tx.commit().await()
                result
            } catch (e: Exception) {
                tx.rollback().await()
                throw e
            }
        } finally {
            conn.close().await()
        }
    }
}