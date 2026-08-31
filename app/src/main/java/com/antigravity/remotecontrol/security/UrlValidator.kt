package com.antigravity.remotecontrol.security

import java.net.URI

object UrlValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val formattedUrl: String? = null,
        val errorMessage: String? = null
    )

    private val EXPLICIT_SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
    private val PORT_CHECK_REGEX = Regex(""":(-?\d+)(?:[/?#]|$)""")

    fun validateAndNormalize(inputUrl: String?): ValidationResult {
        if (inputUrl.isNullOrBlank()) {
            return ValidationResult(isValid = false, errorMessage = "URL cannot be empty")
        }

        var trimmed = inputUrl.trim()
        if (trimmed.contains("\r") || trimmed.contains("\n") || trimmed.contains("\u0000")) {
            return ValidationResult(isValid = false, errorMessage = "URL contains forbidden control characters")
        }

        // Check for non-HTTP(S) explicit schemes like javascript:, file:, data:, mailto:
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("data:") ||
            lower.startsWith("mailto:") || lower.startsWith("tel:") || lower.startsWith("about:") || lower.startsWith("ftp:")) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Unsupported protocol. Only http:// and https:// are allowed."
            )
        }

        if (!trimmed.contains("://")) {
            // Auto-prepend https:// for URLs without explicit scheme
            trimmed = "https://$trimmed"
        }

        // Check port validity in raw string
        val authorityPart = trimmed.substringAfter("://").substringBefore("/").substringBefore("?").substringBefore("#")
        val portMatch = PORT_CHECK_REGEX.find(authorityPart)
        if (portMatch != null) {
            val portValue = portMatch.groupValues[1].toLongOrNull()
            if (portValue == null || portValue < 1 || portValue > 65535) {
                return ValidationResult(isValid = false, errorMessage = "Port number is out of valid range (1-65535)")
            }
        }

        val parsedUri: URI
        try {
            parsedUri = URI(trimmed)
        } catch (e: Exception) {
            return ValidationResult(isValid = false, errorMessage = "Invalid URL format: ${e.message}")
        }

        val scheme = parsedUri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return ValidationResult(isValid = false, errorMessage = "Invalid scheme: $scheme")
        }

        val host = parsedUri.host
        if (host.isNullOrBlank()) {
            return ValidationResult(isValid = false, errorMessage = "Host cannot be empty or malformed")
        }

        if (host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))) {
            return ValidationResult(isValid = false, errorMessage = "IPv6 host must be enclosed in brackets")
        }

        return ValidationResult(isValid = true, formattedUrl = trimmed)
    }
}
