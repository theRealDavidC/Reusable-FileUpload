package com.upload.utils

import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object ObjectKeyGenerator {

    private const val MAX_FILENAME_LENGTH = 255
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy")
    private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MM")
    private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd")

    fun generateUniqueKey(prefix: String? = null, extension: String? = null): String {
        val uuid = UUID.randomUUID().toString()

        val withExtension = if (extension != null) {
            "$uuid.$extension"
        } else {
            uuid
        }

        return if (prefix != null) {
            "$prefix/$withExtension"
        } else {
            withExtension
        }
    }

    fun generateTimestampedKey(
        prefix: String? = null,
        extension: String? = null,
        includeUuid: Boolean = true
    ): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val timestamp = now.format(DATE_FORMAT)

        val filename = if (includeUuid) {
            val uuid = UUID.randomUUID().toString()
            "$timestamp-$uuid"
        } else {
            timestamp
        }

        val withExtension = if (extension != null) {
            "$filename.$extension"
        } else {
            filename
        }

        return if (prefix != null) {
            "$prefix/$withExtension"
        } else {
            withExtension
        }
    }

    fun generateDateHierarchyKey(
        filename: String,
        extension: String? = null,
        includeTime: Boolean = false
    ): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)

        val year = now.format(YEAR_FORMAT)
        val month = now.format(MONTH_FORMAT)
        val day = now.format(DAY_FORMAT)

        val datePath = "$year/$month/$day"

        val finalFilename = if (includeTime) {
            val timestamp = now.format(DATE_FORMAT)
            "${timestamp}_$filename"
        } else {
            filename
        }

        val withExtension = if (extension != null) {
            "$finalFilename.$extension"
        } else {
            finalFilename
        }

        return "$datePath/$withExtension"
    }

    fun generateUserKey(
        userId: String,
        filename: String,
        extension: String? = null
    ): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val timestamp = now.format(DATE_FORMAT)

        val timestampedFilename = "${timestamp}_$filename"

        val withExtension = if (extension != null) {
            "$timestampedFilename.$extension"
        } else {
            timestampedFilename
        }

        return "users/$userId/$withExtension"
    }

    fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .removePrefix("_")
            .removeSuffix("_")
            .take(MAX_FILENAME_LENGTH)
    }
}