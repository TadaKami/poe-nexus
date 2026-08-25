package com.poenexus.http

import com.poenexus.auth.ApiError
import io.vertx.core.Vertx
import io.vertx.core.json.DecodeException
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.vertx.core.json.JsonObject

/** Бизнес-ошибка с HTTP-статусом. */
class HttpError(val status: Int, val code: String, message: String) : RuntimeException(message)

/**
 * База для всех модулей маршрутов: корутин-скоуп на vertx-диспетчере
 * и единая обработка ошибок.
 */
abstract class RouteModule(protected val vertx: Vertx) {

    private val scope = CoroutineScope(vertx.dispatcher() + SupervisorJob())

    protected fun launchSafe(ctx: RoutingContext, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: HttpError) {
                replyError(ctx, e.status, e.code, e.message ?: e.code)
            } catch (e: DecodeException) {
                replyError(ctx, 400, "bad_request", "Invalid JSON body")
            } catch (e: Exception) {
                e.printStackTrace()
                //replyError(ctx, 500, "internal_error", "Internal server error")
                replyError(ctx, 500, "internal_error", e.toString())
            }
        }
    }

    protected fun replyError(ctx: RoutingContext, status: Int, code: String, message: String) {
        if (!ctx.response().ended()) {
            ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "application/json; charset=utf-8")
                .end(JsonObject.mapFrom(ApiError(code, message)).encode())
        }
    }

    /** userId кладёт AuthMiddleware после валидации JWT. */
    protected fun RoutingContext.userId(): String =
        get<String>("userId") ?: throw HttpError(401, "unauthorized", "Not authenticated")
}