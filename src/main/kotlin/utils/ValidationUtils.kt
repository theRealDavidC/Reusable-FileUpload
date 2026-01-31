package com.upload.utils

object ValidationUtils {

    private const val MAX_KEY_LENGTH = 1024
    private const val MIN_BUCKET_LENGTH = 3
    private const val MAX_BUCKET_LENGTH = 63

    fun validateObjectKey(key: String) {
        require(key.isNotBlank()) { "Object key cannot be empty or whitespace" }
        require(key.length <= MAX_KEY_LENGTH) { "Object key exceeds maximum length of $MAX_KEY_LENGTH characters" }
        require(!key.contains('\u0000')) { "Object key cannot contain null bytes" }
        require(!key.contains('\r')) { "Object key cannot contain carriage return characters" }
        require(!key.contains('\n')) { "Object key cannot contain line feed characters" }
        require(!key.startsWith('/')) { "Object key cannot start with a forward slash" }
        require(!key.contains("//")) { "Object key cannot contain consecutive slashes" }
        require(!key.endsWith('/')) { "Object key cannot end with a forward slash" }
    }

    fun validateBucketName(bucket: String) {
        require(bucket.isNotEmpty()) { "Bucket name cannot be empty" }
        require(bucket.length >= MIN_BUCKET_LENGTH) { "Bucket name must be at least $MIN_BUCKET_LENGTH characters" }
        require(bucket.length <= MAX_BUCKET_LENGTH) { "Bucket name cannot exceed $MAX_BUCKET_LENGTH characters" }
        require(bucket.first().isLowercaseLetterOrDigit()) { "Bucket name must start with lowercase letter or digit" }
        require(bucket.last().isLowercaseLetterOrDigit()) { "Bucket name must end with lowercase letter or digit" }
        require(bucket.all { it.isLowercaseLetterOrDigit() || it == '-' || it == '.' }) { "Bucket name can only contain lowercase letters, digits, hyphens, and periods" }
        require(!bucket.contains("..")) { "Bucket name cannot contain consecutive periods" }
    }

    fun validateContentType(contentType: String) {
        require(contentType.isNotEmpty()) { "Content type cannot be empty" }
        require(contentType.contains('/')) { "Content type must contain a forward slash" }

        val parts = contentType.split('/')
        require(parts.size == 2) { "Content type must have exactly one forward slash" }
        require(parts[0].isNotEmpty()) { "Content type must have a type before the slash" }
        require(parts[1].isNotEmpty()) { "Content type must have a subtype after the slash" }
        require(parts.all { it.all { char -> char.isLetterOrDigit() || char in ".-_+" } }) { "Content type contains invalid characters" }
    }

    fun validateFileSize(size: Long, maxSize: Long) {
        require(size >= 0) { "File size cannot be negative" }
        require(size <= maxSize) { "File size ($size bytes) exceeds maximum allowed size ($maxSize bytes)" }
    }

    fun sanitizeObjectKey(key: String): String {
        return key
            .trim()
            .replace("\u0000", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(Regex("/+"), "/")
            .removePrefix("/")
            .removeSuffix("/")
            .take(MAX_KEY_LENGTH)
    }

    private fun Char.isLowercaseLetterOrDigit(): Boolean {
        return this in 'a'..'z' || this in '0'..'9'
    }
}