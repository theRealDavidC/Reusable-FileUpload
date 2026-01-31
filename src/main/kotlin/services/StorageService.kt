package com.upload.services

import com.upload.storage.*
import com.upload.utils.*
import java.time.Instant

class StorageService(
    private val storageProvider: StorageProvider,
    private val config: StorageConfig
) {

    suspend fun uploadFile(request: UploadRequest): UploadResponse {
        return try {
            validateUploadRequest(request)

            val cleanFilename = ObjectKeyGenerator.sanitizeFilename(request.filename)

            val maxSize = config.getMaxSizeForCategory(request.category)
            val actualSize = request.fileData.size.toLong()

            if (actualSize > maxSize) {
                throw FileTooLargeException(maxSize, actualSize)
            }

            val actualContentType = MimeDetector.detect(request.fileData, cleanFilename)

            validateFileTypeForCategory(actualContentType, request.category)

            val storageKey = generateStorageKey(request, cleanFilename)
            ValidationUtils.validateObjectKey(storageKey)

            val checksums = ChecksumUtils.calculateAllChecksums(request.fileData)

            val uploadResult = uploadWithRetry(storageKey, request.fileData, actualContentType)

            verifyUploadSuccess(uploadResult, storageKey, actualSize)

            UploadResponse(
                key = uploadResult.key,
                url = uploadResult.url,
                size = uploadResult.size,
                contentType = actualContentType,
                checksums = checksums,
                uploadedAt = TimeUtils.now(),
                formattedSize = FileSizeUtils.formatSize(uploadResult.size)
            )

        } catch (e: FileTooLargeException) {
            throw e
        } catch (e: InvalidFileTypeException) {
            throw e
        } catch (e: InvalidFilenameException) {
            throw e
        } catch (e: ValidationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException("Unexpected error during upload: ${e.message}", e)
        }
    }

    suspend fun downloadFile(
        key: String,
        verifyIntegrity: Boolean = true,
        expectedChecksums: ChecksumResult? = null
    ): DownloadResponse {
        return try {
            ValidationUtils.validateObjectKey(key)

            val exists = try {
                storageProvider.exists(key)
            } catch (e: Exception) {
                throw StorageException("Failed to check if file exists '$key': ${e.message}", e)
            }

            if (!exists) {
                throw FileNotFoundException(key)
            }

            val fileData = try {
                storageProvider.download(key)
            } catch (e: ObjectNotFoundException) {
                throw FileNotFoundException(key)
            } catch (e: Exception) {
                throw StorageException("Download failed for '$key': ${e.message}", e)
            }

            val metadata = try {
                storageProvider.getMetadata(key)
            } catch (e: ObjectNotFoundException) {
                throw FileNotFoundException(key)
            } catch (e: Exception) {
                throw StorageException("Failed to get metadata for '$key': ${e.message}", e)
            }

            if (fileData.size.toLong() != metadata.size) {
                throw DataCorruptionException(
                    "Size mismatch for '$key': expected ${metadata.size} but got ${fileData.size}"
                )
            }

            val actualChecksums = ChecksumUtils.calculateAllChecksums(fileData)

            val integrityResult = if (verifyIntegrity && expectedChecksums != null) {
                val result = ChecksumUtils.verifyIntegrity(fileData, expectedChecksums)
                if (!result.isValid) {
                    throw ChecksumMismatchException(expectedChecksums, actualChecksums)
                }
                result
            } else {
                null
            }

            DownloadResponse(
                fileData = fileData,
                contentType = metadata.contentType,
                size = fileData.size.toLong(),
                checksums = actualChecksums,
                integrity = integrityResult,
                lastModified = metadata.lastModified
            )

        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: ChecksumMismatchException) {
            throw e
        } catch (e: DataCorruptionException) {
            throw e
        } catch (e: ValidationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException("Unexpected error during download: ${e.message}", e)
        }
    }

    suspend fun generateDownloadUrl(
        key: String,
        expirationMinutes: Int,
        options: DownloadUrlOptions = DownloadUrlOptions()
    ): DownloadUrlResponse {
        return try {
            ValidationUtils.validateObjectKey(key)

            if (expirationMinutes <= 0) {
                throw ValidationException("Expiration must be greater than 0 minutes")
            }
            if (expirationMinutes > config.maxPresignedUrlMinutes) {
                throw ValidationException(
                    "Expiration cannot exceed ${config.maxPresignedUrlMinutes} minutes"
                )
            }

            val exists = try {
                storageProvider.exists(key)
            } catch (e: Exception) {
                throw StorageException("Failed to check if file exists '$key': ${e.message}", e)
            }

            if (!exists) {
                throw FileNotFoundException(key)
            }

            val presignedUrl = try {
                storageProvider.generatePresignedUrl(key, expirationMinutes)
            } catch (e: Exception) {
                throw StorageException(
                    "Failed to generate presigned URL for '$key': ${e.message}",
                    e
                )
            }

            val expiresAt = TimeUtils.calculateExpiration(expirationMinutes)

            DownloadUrlResponse(
                url = presignedUrl,
                expiresAt = expiresAt,
                validForMinutes = expirationMinutes
            )

        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: ValidationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException("Unexpected error generating download URL: ${e.message}", e)
        }
    }

    suspend fun deleteFile(
        key: String,
        deleteVariants: Boolean = false
    ): DeleteResponse {
        return try {
            ValidationUtils.validateObjectKey(key)

            val exists = try {
                storageProvider.exists(key)
            } catch (e: Exception) {
                false
            }

            if (!exists) {
                return DeleteResponse(
                    success = true,
                    deletedKeys = emptyList()
                )
            }

            val mainDeleted = try {
                storageProvider.delete(key)
            } catch (e: Exception) {
                throw StorageException("Failed to delete file '$key': ${e.message}", e)
            }

            if (!mainDeleted) {
                throw StorageException("Failed to delete file '$key': operation returned false")
            }

            val deletedKeys = mutableListOf(key)

            if (deleteVariants) {
                val variantKeys = generateVariantKeys(key)
                for (variantKey in variantKeys) {
                    try {
                        val deleted = storageProvider.delete(variantKey)
                        if (deleted) {
                            deletedKeys.add(variantKey)
                        }
                    } catch (e: Exception) {
                        System.err.println(
                            "Warning: Failed to delete variant '$variantKey': ${e.message}"
                        )
                    }
                }
            }

            DeleteResponse(
                success = true,
                deletedKeys = deletedKeys
            )

        } catch (e: ValidationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException("Unexpected error during delete: ${e.message}", e)
        }
    }

    suspend fun copyFile(
        sourceKey: String,
        destinationKey: String,
        overwrite: Boolean = false
    ): CopyResponse {
        return try {
            ValidationUtils.validateObjectKey(sourceKey)
            ValidationUtils.validateObjectKey(destinationKey)

            val sourceExists = try {
                storageProvider.exists(sourceKey)
            } catch (e: Exception) {
                throw StorageException(
                    "Failed to check if source exists '$sourceKey': ${e.message}",
                    e
                )
            }

            if (!sourceExists) {
                throw FileNotFoundException(sourceKey)
            }

            if (!overwrite) {
                val destExists = try {
                    storageProvider.exists(destinationKey)
                } catch (e: Exception) {
                    false
                }

                if (destExists) {
                    throw FileAlreadyExistsException(destinationKey)
                }
            }

            val newUrl = try {
                storageProvider.copy(sourceKey, destinationKey)
            } catch (e: ObjectNotFoundException) {
                throw FileNotFoundException(sourceKey)
            } catch (e: Exception) {
                throw StorageException(
                    "Copy failed from '$sourceKey' to '$destinationKey': ${e.message}",
                    e
                )
            }

            val sourceMetadata = try {
                storageProvider.getMetadata(sourceKey)
            } catch (e: Exception) {
                throw StorageException("Failed to get source metadata: ${e.message}", e)
            }

            val destinationMetadata = try {
                storageProvider.getMetadata(destinationKey)
            } catch (e: Exception) {
                try {
                    storageProvider.delete(destinationKey)
                } catch (cleanupError: Exception) {
                    System.err.println(
                        "Warning: Failed to cleanup after copy verification failure: " +
                                "${cleanupError.message}"
                    )
                }
                throw StorageException("Failed to get destination metadata: ${e.message}", e)
            }

            if (sourceMetadata.size != destinationMetadata.size) {
                try {
                    storageProvider.delete(destinationKey)
                } catch (cleanupError: Exception) {
                    System.err.println(
                        "Warning: Failed to cleanup corrupted copy: ${cleanupError.message}"
                    )
                }

                throw StorageException(
                    "Copy verification failed: size mismatch " +
                            "(source: ${sourceMetadata.size} bytes, " +
                            "destination: ${destinationMetadata.size} bytes)"
                )
            }

            CopyResponse(
                sourceKey = sourceKey,
                destinationKey = destinationKey,
                url = newUrl,
                size = destinationMetadata.size
            )

        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: FileAlreadyExistsException) {
            throw e
        } catch (e: ValidationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException("Unexpected error during copy: ${e.message}", e)
        }
    }

    suspend fun listFiles(
        prefix: String? = null,
        maxKeys: Int = 1000,
        continuationToken: String? = null
    ): ListResponse {
        return try {
            if (prefix != null) {
                if (prefix.contains('\u0000')) {
                    throw ValidationException("Prefix cannot contain null bytes")
                }
                if (prefix.contains('\r') || prefix.contains('\n')) {
                    throw ValidationException("Prefix cannot contain line breaks")
                }
            }

            val effectiveMaxKeys = when {
                maxKeys <= 0 -> 1000
                maxKeys > 10000 -> 10000
                else -> maxKeys
            }

            val rawObjects = try {
                storageProvider.list(prefix, effectiveMaxKeys)
            } catch (e: Exception) {
                throw StorageException(
                    "Failed to list files${prefix?.let { " with prefix '$it'" } ?: ""}: ${e.message}",
                    e
                )
            }

            val enrichedObjects = rawObjects.map { obj ->
                EnrichedStorageObject(
                    key = obj.key,
                    size = obj.size,
                    lastModified = obj.lastModified,
                    contentType = obj.contentType ?: detectContentTypeFromKey(obj.key),
                    formattedSize = FileSizeUtils.formatSize(obj.size),
                    age = TimeUtils.formatTimeAgo(obj.lastModified)
                )
            }

            val hasMore = rawObjects.size == effectiveMaxKeys
            val nextToken = if (hasMore && rawObjects.isNotEmpty()) {
                rawObjects.last().key
            } else {
                null
            }

            ListResponse(
                objects = enrichedObjects,
                prefix = prefix,
                count = enrichedObjects.size,
                hasMore = hasMore,
                nextToken = nextToken
            )

        } catch (e: ValidationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException("Unexpected error during list: ${e.message}", e)
        }
    }

    suspend fun getFileInfo(key: String): FileInfoResponse {
        return try {
            ValidationUtils.validateObjectKey(key)

            val exists = try {
                storageProvider.exists(key)
            } catch (e: Exception) {
                throw StorageException("Failed to check if file exists '$key': ${e.message}", e)
            }

            if (!exists) {
                throw FileNotFoundException(key)
            }

            val metadata = try {
                storageProvider.getMetadata(key)
            } catch (e: ObjectNotFoundException) {
                throw FileNotFoundException(key)
            } catch (e: Exception) {
                throw StorageException("Failed to get metadata for '$key': ${e.message}", e)
            }

            FileInfoResponse(
                key = key,
                contentType = metadata.contentType,
                size = metadata.size,
                lastModified = metadata.lastModified,
                formattedSize = FileSizeUtils.formatSize(metadata.size),
                age = TimeUtils.formatTimeAgo(metadata.lastModified)
            )

        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: ValidationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException("Unexpected error getting file info: ${e.message}", e)
        }
    }

    suspend fun fileExists(key: String): Boolean {
        return try {
            ValidationUtils.validateObjectKey(key)
            storageProvider.exists(key)
        } catch (e: Exception) {
            false
        }
    }

    private fun validateUploadRequest(request: UploadRequest) {
        if (request.filename.isBlank()) {
            throw InvalidFilenameException("Filename is required and cannot be empty")
        }
        if (request.fileData.isEmpty()) {
            throw InvalidFileSizeException("File data is required and cannot be empty")
        }
        if (request.category.isBlank()) {
            throw ValidationException("Category is required and cannot be empty")
        }
    }

    private fun validateFileTypeForCategory(contentType: String, category: String) {
        val allowedTypes = config.getAllowedTypesForCategory(category)

        val isAllowed = allowedTypes.any { pattern ->
            when {
                pattern == "*" -> true
                pattern.endsWith("/*") -> {
                    val prefix = pattern.substringBefore("/*")
                    contentType.startsWith(prefix)
                }
                else -> contentType == pattern
            }
        }

        if (!isAllowed) {
            throw InvalidFileTypeException(
                "File type '$contentType' is not allowed for category '$category'. " +
                        "Allowed types: ${allowedTypes.joinToString()}"
            )
        }
    }

    private fun generateStorageKey(request: UploadRequest, cleanFilename: String): String {
        if (!request.customKey.isNullOrBlank()) {
            return request.customKey
        }

        val extension = cleanFilename.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() }

        return if (!request.userId.isNullOrBlank()) {
            ObjectKeyGenerator.generateUserKey(
                userId = request.userId,
                filename = cleanFilename,
                extension = extension
            )
        } else {
            ObjectKeyGenerator.generateTimestampedKey(
                prefix = request.category,
                extension = extension,
                includeUuid = true
            )
        }
    }

    private suspend fun uploadWithRetry(
        key: String,
        data: ByteArray,
        contentType: String
    ): UploadResult {
        var lastError: Exception? = null

        for (attempt in 1..3) {
            try {
                return storageProvider.upload(key, data, contentType)
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) {
                    val delayMs = (1000L * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }

        throw StorageException(
            "Upload failed after 3 attempts for key '$key'",
            lastError
        )
    }

    private fun verifyUploadSuccess(result: UploadResult, expectedKey: String, expectedSize: Long) {
        if (result.key != expectedKey) {
            throw StorageException(
                "Upload key mismatch: expected '$expectedKey' but got '${result.key}'"
            )
        }

        if (result.size != expectedSize) {
            throw StorageException(
                "Upload size mismatch: expected $expectedSize bytes but got ${result.size}"
            )
        }
    }

    private fun generateVariantKeys(baseKey: String): List<String> {
        val keyWithoutExtension = baseKey.substringBeforeLast('.')
        val extension = baseKey.substringAfterLast('.', "")

        val sizes = listOf("sm", "md", "lg")
        return sizes.map { size ->
            if (extension.isNotEmpty()) {
                "${keyWithoutExtension}_thumb_${size}.${extension}"
            } else {
                "${keyWithoutExtension}_thumb_${size}"
            }
        }
    }

    private fun detectContentTypeFromKey(key: String): String {
        val filename = key.substringAfterLast('/')
        return MimeDetector.detectFromFilename(filename) ?: "application/octet-stream"
    }
}

data class UploadRequest(
    val filename: String,
    val fileData: ByteArray,
    val category: String,
    val userId: String? = null,
    val customKey: String? = null,
    val makePublic: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UploadRequest
        if (filename != other.filename) return false
        if (!fileData.contentEquals(other.fileData)) return false
        if (category != other.category) return false
        if (userId != other.userId) return false
        if (customKey != other.customKey) return false
        if (makePublic != other.makePublic) return false
        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + fileData.contentHashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + (userId?.hashCode() ?: 0)
        result = 31 * result + (customKey?.hashCode() ?: 0)
        result = 31 * result + makePublic.hashCode()
        return result
    }
}

data class UploadResponse(
    val key: String,
    val url: String,
    val size: Long,
    val contentType: String,
    val checksums: ChecksumResult,
    val uploadedAt: Instant,
    val formattedSize: String
)

data class DownloadResponse(
    val fileData: ByteArray,
    val contentType: String,
    val size: Long,
    val checksums: ChecksumResult,
    val integrity: IntegrityResult?,
    val lastModified: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DownloadResponse
        if (!fileData.contentEquals(other.fileData)) return false
        if (contentType != other.contentType) return false
        if (size != other.size) return false
        if (checksums != other.checksums) return false
        if (integrity != other.integrity) return false
        if (lastModified != other.lastModified) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fileData.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + checksums.hashCode()
        result = 31 * result + (integrity?.hashCode() ?: 0)
        result = 31 * result + lastModified.hashCode()
        return result
    }
}

data class DownloadUrlOptions(
    val contentDisposition: String? = null,
    val customFilename: String? = null
)

data class DownloadUrlResponse(
    val url: String,
    val expiresAt: Instant,
    val validForMinutes: Int
)

data class DeleteResponse(
    val success: Boolean,
    val deletedKeys: List<String>
)

data class CopyResponse(
    val sourceKey: String,
    val destinationKey: String,
    val url: String,
    val size: Long
)

data class EnrichedStorageObject(
    val key: String,
    val size: Long,
    val lastModified: Instant,
    val contentType: String,
    val formattedSize: String,
    val age: String
)

data class ListResponse(
    val objects: List<EnrichedStorageObject>,
    val prefix: String?,
    val count: Int,
    val hasMore: Boolean,
    val nextToken: String?
)

data class FileInfoResponse(
    val key: String,
    val contentType: String,
    val size: Long,
    val lastModified: Instant,
    val formattedSize: String,
    val age: String
)

data class StorageConfig(
    val maxAvatarSize: Long = FileSizeUtils.mbToBytes(5),
    val maxDocumentSize: Long = FileSizeUtils.mbToBytes(50),
    val maxMediaSize: Long = FileSizeUtils.mbToBytes(500),
    val maxGeneralSize: Long = FileSizeUtils.mbToBytes(100),
    val maxPresignedUrlMinutes: Int = 10080,

    val allowedAvatarTypes: List<String> = listOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif"
    ),

    val allowedDocumentTypes: List<String> = listOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain",
        "text/csv"
    ),

    val allowedMediaTypes: List<String> = listOf(
        "image/*",
        "video/*",
        "audio/*"
    ),

    val allowedGeneralTypes: List<String> = listOf("*")
) {
    fun getMaxSizeForCategory(category: String): Long {
        return when (category.lowercase()) {
            "avatar" -> maxAvatarSize
            "document" -> maxDocumentSize
            "media" -> maxMediaSize
            else -> maxGeneralSize
        }
    }

    fun getAllowedTypesForCategory(category: String): List<String> {
        return when (category.lowercase()) {
            "avatar" -> allowedAvatarTypes
            "document" -> allowedDocumentTypes
            "media" -> allowedMediaTypes
            else -> allowedGeneralTypes
        }
    }
}

open class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

class InvalidFilenameException(message: String) : StorageException(message)
class InvalidFileSizeException(message: String) : StorageException(message)
class InvalidFileTypeException(message: String) : StorageException(message)

class FileTooLargeException(
    val maxSize: Long,
    val actualSize: Long
) : StorageException(
    "File size ($actualSize bytes) exceeds maximum allowed size ($maxSize bytes). " +
            "Maximum: ${FileSizeUtils.formatSize(maxSize)}, " +
            "Actual: ${FileSizeUtils.formatSize(actualSize)}"
)

class FileNotFoundException(key: String) : StorageException("File not found: $key")
class FileAlreadyExistsException(key: String) : StorageException("File already exists: $key")
class AccessDeniedException(message: String) : StorageException(message)
class TimeoutException(message: String) : StorageException(message)

class DataCorruptionException(
    message: String,
    val checksums: ChecksumResult? = null
) : StorageException(message)

class ChecksumMismatchException(
    val expected: ChecksumResult,
    val actual: ChecksumResult
) : StorageException(
    "Checksum mismatch detected. " +
            "Expected MD5: ${expected.md5}, Actual MD5: ${actual.md5}"
)

class ConfigurationException(message: String) : StorageException(message)
class ValidationException(message: String) : StorageException(message)
