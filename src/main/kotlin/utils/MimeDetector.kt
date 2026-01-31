package com.upload.utils

object MimeDetector {

    private val extensionMap = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "bmp" to "image/bmp",
        "ico" to "image/x-icon",
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "txt" to "text/plain",
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "text/javascript",
        "json" to "application/json",
        "xml" to "application/xml",
        "csv" to "text/csv",
        "zip" to "application/zip",
        "rar" to "application/x-rar-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "7z" to "application/x-7z-compressed",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "mp4" to "video/mp4",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "webm" to "video/webm"
    )

    fun detectFromFilename(filename: String): String? {
        val extension = filename.substringAfterLast('.', "").lowercase()

        if (extension.isEmpty()) return null

        return extensionMap[extension]
    }

    fun detectFromContent(data: ByteArray): String? {
        if (data.isEmpty()) return null

        return when {
            data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte() -> {
                "image/jpeg"
            }
            data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
                    data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() &&
                    data[4] == 0x0D.toByte() && data[5] == 0x0A.toByte() &&
                    data[6] == 0x1A.toByte() && data[7] == 0x0A.toByte() -> {
                "image/png"
            }
            data.size >= 5 && data[0] == 0x25.toByte() && data[1] == 0x50.toByte() &&
                    data[2] == 0x44.toByte() && data[3] == 0x46.toByte() && data[4] == 0x2D.toByte() -> {
                "application/pdf"
            }
            data.size >= 4 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte() &&
                    data[2] == 0x03.toByte() && data[3] == 0x04.toByte() -> {
                "application/zip"
            }
            data.size >= 4 && data[0] == 0x47.toByte() && data[1] == 0x49.toByte() &&
                    data[2] == 0x46.toByte() && data[3] == 0x38.toByte() -> {
                "image/gif"
            }
            data.size >= 2 && data[0] == 0x42.toByte() && data[1] == 0x4D.toByte() -> {
                "image/bmp"
            }
            data.size >= 4 && data[0] == 0x52.toByte() && data[1] == 0x61.toByte() &&
                    data[2] == 0x72.toByte() && data[3] == 0x21.toByte() -> {
                "application/x-rar-compressed"
            }
            data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte() -> {
                "application/gzip"
            }
            looksLikeText(data) -> {
                "text/plain"
            }
            else -> null
        }
    }

    fun detect(data: ByteArray, filename: String?): String {
        detectFromContent(data)?.let { return it }

        filename?.let {
            detectFromFilename(it)?.let { return it }
        }

        return "application/octet-stream"
    }

    fun isImage(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    fun isVideo(mimeType: String): Boolean {
        return mimeType.startsWith("video/")
    }

    fun isDocument(mimeType: String): Boolean {
        return mimeType == "application/pdf" ||
                mimeType.contains("word") ||
                mimeType.contains("excel") ||
                mimeType.contains("powerpoint") ||
                mimeType.contains("opendocument")
    }

    fun validateMimeType(data: ByteArray, declaredMimeType: String): Boolean {
        val detectedMimeType = detectFromContent(data) ?: return true

        if (detectedMimeType == declaredMimeType) return true

        if (declaredMimeType == "application/octet-stream") return true

        val detectedCategory = detectedMimeType.substringBefore('/')
        val declaredCategory = declaredMimeType.substringBefore('/')

        return detectedCategory == declaredCategory
    }

    private fun looksLikeText(data: ByteArray): Boolean {
        if (data.isEmpty()) return false

        val sampleSize = minOf(512, data.size)
        var printableCount = 0

        for (i in 0 until sampleSize) {
            val byte = data[i].toInt() and 0xFF
            if (byte in 9..13 || byte in 32..126) {
                printableCount++
            }
        }

        return printableCount.toDouble() / sampleSize > 0.85
    }
}