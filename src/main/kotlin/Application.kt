package com.upload

import com.upload.plugins.*
import com.upload.services.StorageConfig
import com.upload.services.StorageService
import com.upload.storage.R2StorageProvider
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val accountId = System.getenv("R2_ACCOUNT_ID")
        ?: throw IllegalStateException("R2_ACCOUNT_ID environment variable is required")

    val bucket = System.getenv("R2_BUCKET")
        ?: throw IllegalStateException("R2_BUCKET environment variable is required")

    val accessKey = System.getenv("R2_ACCESS_KEY")
        ?: throw IllegalStateException("R2_ACCESS_KEY environment variable is required")

    val secretKey = System.getenv("R2_SECRET_KEY")
        ?: throw IllegalStateException("R2_SECRET_KEY environment variable is required")

    val publicDomain = System.getenv("R2_PUBLIC_DOMAIN")

    val storageProvider = R2StorageProvider(
        accountId = accountId,
        bucket = bucket,
        accessKey = accessKey,
        secretKey = secretKey,
        publicDomain = publicDomain
    )

    val storageConfig = StorageConfig()
    val storageService = StorageService(storageProvider, storageConfig)

    configureSerialization()
    configureCORS()
    configureLogging()
    configureExceptionHandling()
    configureRouting(storageService)

    environment.monitor.subscribe(ApplicationStopped) {
        storageProvider.close()
    }
}