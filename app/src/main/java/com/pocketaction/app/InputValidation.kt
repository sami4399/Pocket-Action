package com.pocketaction.app

import java.net.URI
import java.util.Locale

/** Validates external web destinations before Pocket Action creates an ACTION_VIEW intent. */
object InputValidation {
    fun normalizeHttpUrl(value: String): String? {
        val hasExplicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value)
        val isHttpScheme = value.startsWith("http://", true) || value.startsWith("https://", true)
        if (hasExplicitScheme && !isHttpScheme) return null
        val candidate = if (isHttpScheme) {
            value
        } else {
            "https://$value"
        }
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        return candidate
    }
}
