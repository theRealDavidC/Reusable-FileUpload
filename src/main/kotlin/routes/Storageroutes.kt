package com.upload.routes

import com.upload.dto.*
import com.upload.services.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.storageRoutes(storageService: StorageService) {

    route("/api/storage") {

        post("/upload") {
            val multipart = call.receiveMultipart()
            var uploadRequest: UploadRequestDTO? = null
            var fileBytes: ByteArray? = null
            var filename: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "metadata") {
                            uploadRequest = kotlinx.serialization.json.Json.decodeFromString(part.value)
                        }
                    }
                    is PartData.FileItem -> {
                        filename = part.originalFileName ?: "unnamed"
                        fileBytes = part.streamProvider().readBytes()
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (uploadRequest == null || fileBytes == null || filename == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDTO(
                        error = "InvalidRequest",
                        message = "Missing required fields: metadata and file"
                    )
                )
                return@post
            }

            val request = UploadRequest(
                filename = filename!!,
                fileData = fileBytes!!,
                category = uploadRequest!!.category,
                userId = uploadRequest!!.userId,
                customKey = uploadRequest!!.customKey,
                makePublic = uploadRequest!!.makePublic
            )

            val response = storageService.uploadFile(request)
            call.respond(HttpStatusCode.Created, StorageMapper.toUploadResponseDTO(response))
        }

        get("/download/{key}") {
            val key = call.parameters["key"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDTO(error = "InvalidRequest", message = "Missing key parameter")
                )
                return@get
            }

            val response = storageService.downloadFile(key)

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    key.substringAfterLast('/')
                ).toString()
            )

            call.respondBytes(
                bytes = response.fileData,
                contentType = ContentType.parse(response.contentType)
            )
        }

        post("/download-url/{key}") {
            val key = call.parameters["key"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDTO(error = "InvalidRequest", message = "Missing key parameter")
                )
                return@post
            }

            val request = call.receive<DownloadUrlRequestDTO>()
            val response = storageService.generateDownloadUrl(key, request.expirationMinutes)
            call.respond(StorageMapper.toDownloadUrlResponseDTO(response))
        }

        get("/info/{key}") {
            val key = call.parameters["key"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDTO(error = "InvalidRequest", message = "Missing key parameter")
                )
                return@get
            }

            val response = storageService.getFileInfo(key)
            call.respond(StorageMapper.toFileInfoResponseDTO(response))
        }

        get("/exists/{key}") {
            val key = call.parameters["key"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDTO(error = "InvalidRequest", message = "Missing key parameter")
                )
                return@get
            }

            val exists = storageService.fileExists(key)
            call.respond(mapOf("exists" to exists, "key" to key))
        }

        get("/list") {
            val prefix = call.request.queryParameters["prefix"]
            val maxKeys = call.request.queryParameters["maxKeys"]?.toIntOrNull() ?: 1000
            val continuationToken = call.request.queryParameters["continuationToken"]

            val response = storageService.listFiles(prefix, maxKeys, continuationToken)
            call.respond(StorageMapper.toListResponseDTO(response))
        }

        post("/copy/{sourceKey}") {
            val sourceKey = call.parameters["sourceKey"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDTO(error = "InvalidRequest", message = "Missing sourceKey parameter")
                )
                return@post
            }

            val request = call.receive<CopyRequestDTO>()
            val response = storageService.copyFile(
                sourceKey = sourceKey,
                destinationKey = request.destinationKey,
                overwrite = request.overwrite
            )
            call.respond(StorageMapper.toCopyResponseDTO(response))
        }

        delete("/{key}") {
            val key = call.parameters["key"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDTO(error = "InvalidRequest", message = "Missing key parameter")
                )
                return@delete
            }

            val deleteVariants = call.request.queryParameters["deleteVariants"]?.toBoolean() ?: false
            val response = storageService.deleteFile(key, deleteVariants)
            call.respond(StorageMapper.toDeleteResponseDTO(response))
        }
    }

    get("/health") {
        call.respond(
            HealthResponseDTO(
                status = "healthy",
                timestamp = java.time.Instant.now().toString()
            )
        )
    }
}