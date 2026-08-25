plugins {
    kotlin("jvm") version "1.9.24"
    application
}

group = "com.poenexus"
version = "0.1.0"

repositories {
    mavenCentral()
}

val vertxVersion = "4.5.8"

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))

    // Vert.x core & web
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-web-client")

    // Kotlin coroutines bridge
    implementation("io.vertx:vertx-lang-kotlin-coroutines")

    // Хеширование паролей: Argon2id
    implementation("de.mkammerer:argon2-jvm:2.12")    
    
    // Данные: PostgreSQL + Redis
    implementation("io.vertx:vertx-pg-client")
    implementation("io.vertx:vertx-redis-client")

    // Конфигурация
    implementation("io.vertx:vertx-config")

    // JSON для Kotlin data-классов
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

    // Логирование
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Тесты
    testImplementation("io.vertx:vertx-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("com.poenexus.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}