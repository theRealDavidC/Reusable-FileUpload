package com.upload.storage

import java.time.Instant

interface StorageProvider : AutoCloseable {
    suspend fun upload(key: String, content: ByteArray, contentType: String): UploadResult
    suspend fun download(key: String): ByteArray
    suspend fun exists(key: String): Boolean
    suspend fun getMetadata(key: String): StorageMetadata
    suspend fun list(prefix: String? = null, maxKeys: Int = 1000): List<StorageObject>
    suspend fun copy(sourceKey: String, destinationKey: String): String
    suspend fun delete(key: String): Boolean
    suspend fun generatePresignedUrl(key: String, expirationMinutes: Int): String
    override fun close()
}

data class UploadResult(
    val key: String,
    val url: String,
    val size: Long
)

data class StorageObject(
    val key: String,
    val size: Long,
    val lastModified: Instant,
    val contentType: String?
)

data class StorageMetadata(
    val contentType: String,
    val size: Long,
    val lastModified: Instant
)



open class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ObjectNotFoundException(key: String) : StorageException("Object not found: $key")
class StorageOperationException(message: String, cause: Throwable? = null) : StorageException(message, cause)
