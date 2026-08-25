package com.poenexus
 
import com.poenexus.http.HttpServerVerticle
import io.vertx.core.Vertx

 fun main() {
     val vertx = Vertx.vertx()
 

    // Модуль 1: HTTP-сервер + авторизация + Нексусы
    vertx.deployVerticle(HttpServerVerticle())
    println("PoE Nexus backend: verticles deployed")
}