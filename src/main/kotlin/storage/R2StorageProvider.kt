package com.upload.storage

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

class R2StorageProvider(
    private val accountId: String,
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String,
    private val publicDomain: String? = null
) : StorageProvider {

    private val endpoint = "https://${accountId}.r2.cloudflarestorage.com"

    private val s3Client = S3Client {
        region = "auto"
        endpointUrl = Url.parse(this@R2StorageProvider.endpoint)
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = this@R2StorageProvider.accessKey
            secretAccessKey = this@R2StorageProvider.secretKey
        }
    }

    private val isClosed = AtomicBoolean(false)

    override suspend fun upload(key: String, content: ByteArray, contentType: String): UploadResult {
        checkNotClosed()

        return try {
            val response = s3Client.putObject(PutObjectRequest {
                bucket = this@R2StorageProvider.bucket
                this.key = key
                body = ByteStream.fromBytes(content)
                metadata = mapOf("Content-Type" to contentType)
            })

            if (response.eTag.isNullOrBlank()) {
                throw StorageOperationException("Upload failed: No ETag returned from R2")
            }

            val url = publicDomain?.let { "https://$it/$key" }
                ?: "$endpoint/${this@R2StorageProvider.bucket}/$key"

            UploadResult(key, url, content.size.toLong())

        } catch (e: StorageOperationException) {
            throw e
        } catch (e: S3Exception) {
            throw StorageOperationException("R2 upload failed for '$key': ${e.message}", e)
        } catch (e: Exception) {
            throw StorageOperationException("Unexpected error uploading '$key': ${e.message}", e)
        }
    }

    override suspend fun download(key: String): ByteArray {
        checkNotClosed()

        return try {
            s3Client.getObject(GetObjectRequest {
                bucket = this@R2StorageProvider.bucket
                this.key = key
            }) { response ->
                response.body?.toByteArray() ?: throw ObjectNotFoundException(key)
            }
        } catch (e: NoSuchKey) {
            throw ObjectNotFoundException(key)
        } catch (e: ObjectNotFoundException) {
            throw e
        } catch (e: S3Exception) {
            throw StorageOperationException("R2 download failed for '$key': ${e.message}", e)
        } catch (e: Exception) {
            throw StorageOperationException("Unexpected error downloading '$key': ${e.message}", e)
        }
    }

    override suspend fun exists(key: String): Boolean {
        checkNotClosed()

        return try {
            s3Client.headObject(HeadObjectRequest {
                bucket = this@R2StorageProvider.bucket
                this.key = key
            })
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getMetadata(key: String): StorageMetadata {
        checkNotClosed()

        return try {
            val response = s3Client.headObject(HeadObjectRequest {
                bucket = this@R2StorageProvider.bucket
                this.key = key
            })

            StorageMetadata(
                contentType = response.contentType ?: "application/octet-stream",
                size = response.contentLength ?: 0L,
                lastModified = response.lastModified?.let {
                    java.time.Instant.ofEpochMilli(it.epochMilliseconds)
                } ?: java.time.Instant.now()
            )
        } catch (e: NoSuchKey) {
            throw ObjectNotFoundException(key)
        } catch (e: NotFound) {
            throw ObjectNotFoundException(key)
        } catch (e: S3Exception) {
            throw StorageOperationException("Failed to get metadata for '$key': ${e.message}", e)
        }
    }

    override suspend fun list(prefix: String?, maxKeys: Int): List<StorageObject> {
        checkNotClosed()

        return try {
            val response = s3Client.listObjectsV2(ListObjectsV2Request {
                bucket = this@R2StorageProvider.bucket
                this.prefix = prefix  // ✅ FIXED: Use this.prefix
                this.maxKeys = maxKeys
            })

            response.contents?.map { obj ->
                StorageObject(
                    key = obj.key ?: "",
                    size = obj.size ?: 0L,
                    lastModified = obj.lastModified?.let {
                        java.time.Instant.ofEpochMilli(it.epochMilliseconds)
                    } ?: java.time.Instant.now(),
                    contentType = null
                )
            } ?: emptyList()

        } catch (e: S3Exception) {
            val prefixMsg = prefix?.let { " with prefix '$it'" } ?: ""
            throw StorageOperationException("Failed to list objects$prefixMsg: ${e.message}", e)
        }
    }

    override suspend fun copy(sourceKey: String, destinationKey: String): String {
        checkNotClosed()

        return try {
            val copySource = "${this@R2StorageProvider.bucket}/$sourceKey"

            val response = s3Client.copyObject(CopyObjectRequest {
                bucket = this@R2StorageProvider.bucket
                key = destinationKey
                this.copySource = copySource
            })

            if (response.copyObjectResult?.eTag.isNullOrBlank()) {
                throw StorageOperationException("Copy failed: No ETag in response")
            }

            publicDomain?.let { "https://$it/$destinationKey" }
                ?: "$endpoint/${this@R2StorageProvider.bucket}/$destinationKey"

        } catch (e: NoSuchKey) {
            throw ObjectNotFoundException(sourceKey)
        } catch (e: StorageOperationException) {
            throw e
        } catch (e: S3Exception) {
            throw StorageOperationException("R2 copy failed from '$sourceKey' to '$destinationKey': ${e.message}", e)
        } catch (e: Exception) {
            throw StorageOperationException("Unexpected error copying '$sourceKey': ${e.message}", e)
        }
    }

    override suspend fun delete(key: String): Boolean {
        checkNotClosed()

        return try {
            s3Client.deleteObject(DeleteObjectRequest {
                bucket = this@R2StorageProvider.bucket
                this.key = key
            })
            true
        } catch (e: S3Exception) {
            false
        }
    }

    override suspend fun generatePresignedUrl(key: String, expirationMinutes: Int): String {
        checkNotClosed()

        return try {
            val request = GetObjectRequest {
                bucket = this@R2StorageProvider.bucket
                this.key = key
            }

            val presignedRequest = s3Client.presignGetObject(request, expirationMinutes.minutes)
            presignedRequest.url.toString()

        } catch (e: S3Exception) {
            throw StorageOperationException("Failed to generate presigned URL for '$key': ${e.message}", e)
        } catch (e: Exception) {
            throw StorageOperationException("Unexpected error generating URL for '$key': ${e.message}", e)
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                s3Client.close()
            } catch (e: Exception) {
                System.err.println("Error closing R2 S3Client: ${e.message}")
            }
        }
    }

    private fun checkNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("StorageProvider has been closed and cannot be used")
        }
    }
}