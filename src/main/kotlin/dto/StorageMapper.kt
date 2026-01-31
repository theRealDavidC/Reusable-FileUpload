package com.upload.dto

import com.upload.services.*
import java.time.format.DateTimeFormatter

object StorageMapper {

    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    fun toUploadResponseDTO(response: UploadResponse): UploadResponseDTO {
        return UploadResponseDTO(
            key = response.key,
            url = response.url,
            size = response.size,
            contentType = response.contentType,
            md5 = response.checksums.md5,
            sha256 = response.checksums.sha256,
            uploadedAt = isoFormatter.format(response.uploadedAt),
            formattedSize = response.formattedSize
        )
    }

    fun toDownloadUrlResponseDTO(response: DownloadUrlResponse): DownloadUrlResponseDTO {
        return DownloadUrlResponseDTO(
            url = response.url,
            expiresAt = isoFormatter.format(response.expiresAt),
            validForMinutes = response.validForMinutes
        )
    }

    fun toFileInfoResponseDTO(response: FileInfoResponse): FileInfoResponseDTO {
        return FileInfoResponseDTO(
            key = response.key,
            contentType = response.contentType,
            size = response.size,
            lastModified = isoFormatter.format(response.lastModified),
            formattedSize = response.formattedSize,
            age = response.age
        )
    }

    fun toListResponseDTO(response: ListResponse): ListResponseDTO {
        return ListResponseDTO(
            objects = response.objects.map { toStorageObjectDTO(it) },
            prefix = response.prefix,
            count = response.count,
            hasMore = response.hasMore,
            nextToken = response.nextToken
        )
    }

    fun toStorageObjectDTO(obj: EnrichedStorageObject): StorageObjectDTO {
        return StorageObjectDTO(
            key = obj.key,
            size = obj.size,
            lastModified = isoFormatter.format(obj.lastModified),
            contentType = obj.contentType,
            formattedSize = obj.formattedSize,
            age = obj.age
        )
    }

    fun toCopyResponseDTO(response: CopyResponse): CopyResponseDTO {
        return CopyResponseDTO(
            sourceKey = response.sourceKey,
            destinationKey = response.destinationKey,
            url = response.url,
            size = response.size
        )
    }

    fun toDeleteResponseDTO(response: DeleteResponse): DeleteResponseDTO {
        return DeleteResponseDTO(
            success = response.success,
            deletedKeys = response.deletedKeys
        )
    }
}