package com.upload.utils

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TimeUtils {

    private val ISO8601_FORMATTER = DateTimeFormatter.ISO_INSTANT
    private val FILENAME_SAFE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

    fun now(): Instant {
        return Instant.now()
    }

    fun nowMillis(): Long {
        return Instant.now().toEpochMilli()
    }

    fun toISO8601(instant: Instant): String {
        return ISO8601_FORMATTER.format(instant)
    }

    fun toFilenameSafe(instant: Instant): String {
        return FILENAME_SAFE_FORMATTER.format(instant)
    }

    fun calculateExpiration(minutes: Int): Instant {
        val currentTime = now()
        val seconds = minutes * 60L
        return currentTime.plusSeconds(seconds)
    }

    fun isExpired(expirationTime: Instant): Boolean {
        val currentTime = now()
        return currentTime.isAfter(expirationTime)
    }

    fun timeUntilExpiration(expirationTime: Instant): Duration {
        val currentTime = now()
        val duration = Duration.between(currentTime, expirationTime)

        return if (duration.isNegative) {
            Duration.ZERO
        } else {
            duration
        }
    }

    fun calculateAge(lastModified: Instant): Duration {
        val currentTime = now()
        return Duration.between(lastModified, currentTime)
    }

    fun formatDuration(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        val parts = mutableListOf<String>()

        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")

        return parts.joinToString(" ")
    }

    fun formatTimeAgo(instant: Instant): String {
        val age = calculateAge(instant)

        val totalDays = age.toDays()
        val totalHours = age.toHours()
        val totalMinutes = age.toMinutes()

        return when {
            totalDays > 365 -> {
                val years = totalDays / 365
                "$years year(s) ago"
            }
            totalDays > 30 -> {
                val months = totalDays / 30
                "$months month(s) ago"
            }
            totalDays > 0 -> {
                "$totalDays day(s) ago"
            }
            totalHours > 0 -> {
                "$totalHours hour(s) ago"
            }
            totalMinutes > 0 -> {
                "$totalMinutes minute(s) ago"
            }
            else -> {
                "just now"
            }
        }
    }
}