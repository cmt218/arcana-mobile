package org.arcana.mobile.navigation

/**
 * Pure, side-effect-free parser for the welcome / signup-completion deep links.
 *
 * Routing (navigating to the signup screen with the extracted token) is wired
 * with the platform entry points (MainActivity / IosDeepLinkBridge) and the
 * session controller — this object only turns an incoming string into a token (or
 * `null`). It has no platform dependencies and no stdlib URL parser (none
 * exists in KMP `commonMain`), so the two known URL shapes are parsed by hand.
 *
 * [extractWelcomeToken] accepts BOTH:
 *  1. Production Universal Link / App Link: `https://arcana.fit/welcome?token=XXX`
 *     (host == `arcana.fit`, path == `/welcome` or `/welcome/`).
 *  2. Local-dev custom scheme:             `arcana://welcome?token=XXX`
 *     (authority == `welcome`, no path).
 */
object DeepLinkHandler {
    private const val WELCOME_HOST = "arcana.fit"
    private const val WELCOME_PATH = "/welcome"
    private const val CUSTOM_SCHEME = "arcana://"
    private const val CUSTOM_AUTHORITY = "welcome"

    fun extractWelcomeToken(url: String): String? {
        val schemeIdx = url.indexOf("://")
        if (schemeIdx < 0) return null
        val scheme = url.substring(0, schemeIdx).lowercase()
        val rest = url.substring(schemeIdx + 3)

        // Split authority from the path/query/fragment remainder.
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (authorityEnd >= 0) rest.substring(0, authorityEnd) else rest
        val remainder = if (authorityEnd >= 0) rest.substring(authorityEnd) else ""

        val query: String = when (scheme) {
            "arcana" -> {
                // Custom scheme: target is identified by authority == "welcome",
                // no path requirement. Anything before '?' (other than an empty
                // string or a bare '/') would be an unexpected path — reject it.
                if (authority.lowercase() != CUSTOM_AUTHORITY) return null
                val path = remainder.substringBefore('?').substringBefore('#')
                if (path.isNotEmpty() && path != "/") return null
                extractQuery(remainder)
            }
            "http", "https" -> {
                if (authority.lowercase() != WELCOME_HOST) return null
                val path = remainder.substringBefore('?').substringBefore('#')
                if (path != WELCOME_PATH && path != "$WELCOME_PATH/") return null
                extractQuery(remainder)
            }
            else -> return null
        }

        return tokenFromQuery(query)
    }

    /** Returns the query string (between '?' and '#'), or "" if there is none. */
    private fun extractQuery(remainder: String): String {
        val queryStart = remainder.indexOf('?')
        if (queryStart < 0) return ""
        return remainder.substring(queryStart + 1).substringBefore('#')
    }

    /** Finds the `token` param in an `a=b&c=d` query and URL-decodes its value. */
    private fun tokenFromQuery(query: String): String? {
        for (param in query.split('&')) {
            val eq = param.indexOf('=')
            if (eq < 0) continue
            if (param.substring(0, eq) == "token") return urlDecode(param.substring(eq + 1)).takeIf { it.isNotEmpty() }
        }
        return null
    }

    // Assumes ASCII token values; does not reassemble multi-byte UTF-8 percent-escapes (tokens are URL-safe base64).
    private fun urlDecode(s: String): String {
        val out = StringBuilder(); var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '+' -> out.append(' ')
                '%' -> if (i + 2 < s.length) {
                    val byte = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (byte != null) { out.append(byte.toChar()); i += 2 } else out.append(c)
                } else out.append(c)
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
