package com.example.kooggen.backend

import com.example.kooggen.backend.routes.generateRoute
import com.example.kooggen.backend.routes.optionsRoute
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        staticResources("/", "static") {
            default("index.html")
        }

        route("/api") {
            optionsRoute()
            generateRoute()
        }
    }
}
