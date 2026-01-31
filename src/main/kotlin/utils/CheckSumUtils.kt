package com.upload.utils

import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32

object ChecksumUtils {

    fun calculateMD5(data: ByteArray): String {
        val md5 = MessageDigest.getInstance("MD5")
        val hashBytes = md5.digest(data)
        return hashBytes.toHexString()
    }

    fun calculateSHA256(data: ByteArray): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hashBytes = sha256.digest(data)
        return hashBytes.toHexString()
    }

    fun calculateMD5Base64(data: ByteArray): String {
        val md5 = MessageDigest.getInstance("MD5")
        val hashBytes = md5.digest(data)
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    fun verifyMD5(data: ByteArray, expectedHash: String): Boolean {
        val calculatedHash = calculateMD5(data)
        return calculatedHash.lowercase() == expectedHash.lowercase()
    }

    fun calculateCRC32(data: ByteArray): Long {
        val crc32 = CRC32()
        crc32.update(data)
        return crc32.value
    }

    fun calculateAllChecksums(data: ByteArray): ChecksumResult {
        return ChecksumResult(
            md5 = calculateMD5(data),
            sha256 = calculateSHA256(data),
            crc32 = calculateCRC32(data),
            size = data.size.toLong()
        )
    }

    fun verifyIntegrity(data: ByteArray, expected: ChecksumResult): IntegrityResult {
        val calculated = calculateAllChecksums(data)

        val md5Match = calculated.md5.lowercase() == expected.md5.lowercase()
        val sha256Match = calculated.sha256.lowercase() == expected.sha256.lowercase()
        val crc32Match = calculated.crc32 == expected.crc32
        val sizeMatch = calculated.size == expected.size

        return IntegrityResult(
            isValid = md5Match && sha256Match && crc32Match && sizeMatch,
            md5Match = md5Match,
            sha256Match = sha256Match,
            crc32Match = crc32Match,
            sizeMatch = sizeMatch
        )
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}

data class ChecksumResult(
    val md5: String,
    val sha256: String,
    val crc32: Long,
    val size: Long
)

data class IntegrityResult(
    val isValid: Boolean,
    val md5Match: Boolean,
    val sha256Match: Boolean,
    val crc32Match: Boolean,
    val sizeMatch: Boolean
)