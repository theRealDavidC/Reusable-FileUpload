package com.upload.dto

import kotlinx.serialization.Serializable

@Serializable
data class UploadRequestDTO(
    val category: String,
    val userId: String? = null,
    val customKey: String? = null,
    val makePublic: Boolean = false
)

@Serializable
data class UploadResponseDTO(
    val key: String,
    val url: String,
    val size: Long,
    val contentType: String,
    val md5: String,
    val sha256: String,
    val uploadedAt: String,
    val formattedSize: String
)

@Serializable
data class DownloadUrlRequestDTO(
    val expirationMinutes: Int = 60
)

@Serializable
data class DownloadUrlResponseDTO(
    val url: String,
    val expiresAt: String,
    val validForMinutes: Int
)

@Serializable
data class FileInfoResponseDTO(
    val key: String,
    val contentType: String,
    val size: Long,
    val lastModified: String,
    val formattedSize: String,
    val age: String
)

@Serializable
data class ListResponseDTO(
    val objects: List<StorageObjectDTO>,
    val prefix: String?,
    val count: Int,
    val hasMore: Boolean,
    val nextToken: String?
)

@Serializable
data class StorageObjectDTO(
    val key: String,
    val size: Long,
    val lastModified: String,
    val contentType: String,
    val formattedSize: String,
    val age: String
)

@Serializable
data class CopyRequestDTO(
    val destinationKey: String,
    val overwrite: Boolean = false
)

@Serializable
data class CopyResponseDTO(
    val sourceKey: String,
    val destinationKey: String,
    val url: String,
    val size: Long
)

@Serializable
data class DeleteResponseDTO(
    val success: Boolean,
    val deletedKeys: List<String>
)

@Serializable
data class ErrorResponseDTO(
    val error: String,
    val message: String,
    val details: String? = null
)

@Serializable
data class HealthResponseDTO(
    val status: String,
    val timestamp: String
)