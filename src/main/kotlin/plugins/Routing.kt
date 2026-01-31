package com.upload.plugins

import com.upload.routes.storageRoutes
import com.upload.services.StorageService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting(storageService: StorageService) {
    routing {
        storageRoutes(storageService)
    }
}