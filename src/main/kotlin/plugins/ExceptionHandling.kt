package com.upload.plugins

import com.upload.dto.ErrorResponseDTO
import com.upload.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureExceptionHandling() {
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(
                    error = "ValidationError",
                    message = cause.message ?: "Validation failed"
                )
            )
        }

        exception<InvalidFilenameException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(
                    error = "InvalidFilename",
                    message = cause.message ?: "Invalid filename"
                )
            )
        }

        exception<InvalidFileSizeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(
                    error = "InvalidFileSize",
                    message = cause.message ?: "Invalid file size"
                )
            )
        }

        exception<InvalidFileTypeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDTO(
                    error = "InvalidFileType",
                    message = cause.message ?: "Invalid file type"
                )
            )
        }

        exception<FileTooLargeException> { call, cause ->
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponseDTO(
                    error = "FileTooLarge",
                    message = cause.message ?: "File too large"
                )
            )
        }

        exception<FileNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDTO(
                    error = "FileNotFound",
                    message = cause.message ?: "File not found"
                )
            )
        }

        exception<FileAlreadyExistsException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponseDTO(
                    error = "FileAlreadyExists",
                    message = cause.message ?: "File already exists"
                )
            )
        }

        exception<AccessDeniedException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponseDTO(
                    error = "AccessDenied",
                    message = cause.message ?: "Access denied"
                )
            )
        }

        exception<TimeoutException> { call, cause ->
            call.respond(
                HttpStatusCode.RequestTimeout,
                ErrorResponseDTO(
                    error = "Timeout",
                    message = cause.message ?: "Request timeout"
                )
            )
        }

        exception<DataCorruptionException> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDTO(
                    error = "DataCorruption",
                    message = cause.message ?: "Data corruption detected"
                )
            )
        }

        exception<ChecksumMismatchException> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDTO(
                    error = "ChecksumMismatch",
                    message = cause.message ?: "Checksum mismatch"
                )
            )
        }

        exception<ConfigurationException> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDTO(
                    error = "ConfigurationError",
                    message = cause.message ?: "Configuration error"
                )
            )
        }

        exception<StorageException> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDTO(
                    error = "StorageError",
                    message = cause.message ?: "Storage operation failed"
                )
            )
        }

        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDTO(
                    error = "InternalServerError",
                    message = "An unexpected error occurred"
                )
            )
        }
    }
}