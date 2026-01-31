package com.upload.utils

import kotlin.math.pow

object FileSizeUtils {

    private val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    private const val UNIT_SIZE = 1024.0

    fun formatSize(bytes: Long, decimals: Int = 2): String {
        if (bytes < 0) return "Invalid size"
        if (bytes == 0L) return "0 B"

        var unitIndex = 0
        var size = bytes.toDouble()

        while (size >= UNIT_SIZE && unitIndex < units.size - 1) {
            size /= UNIT_SIZE
            unitIndex++
        }

        val formatted = "%.${decimals}f".format(size)
        return "$formatted ${units[unitIndex]}"
    }

    fun parseSize(sizeString: String): Long {
        val normalized = sizeString.trim().uppercase().replace(" ", "")

        val regex = """^(\d+\.?\d*)\s*([A-Z]*)$""".toRegex()
        val matchResult = regex.find(normalized)
            ?: throw IllegalArgumentException("Invalid size format: $sizeString")

        val number = matchResult.groupValues[1].toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid number in size: $sizeString")

        val unit = matchResult.groupValues[2].ifEmpty { "B" }

        val multiplier = when (unit) {
            "B" -> 1.0
            "KB" -> UNIT_SIZE
            "MB" -> UNIT_SIZE.pow(2)
            "GB" -> UNIT_SIZE.pow(3)
            "TB" -> UNIT_SIZE.pow(4)
            "PB" -> UNIT_SIZE.pow(5)
            else -> throw IllegalArgumentException("Unknown unit: $unit")
        }

        return (number * multiplier).toLong()
    }

    fun mbToBytes(megabytes: Int): Long {
        return megabytes * 1024L * 1024L
    }

    fun mbToBytes(megabytes: Double): Long {
        return (megabytes * 1024.0 * 1024.0).toLong()
    }
}