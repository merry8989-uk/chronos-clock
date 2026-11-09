package com.osintgram.chronos.diagnostics

/**
 * Strips secrets and personal data out of a log line before it is written.
 *
 * Logs are meant to be shared when something breaks, so the masking has to
 * happen on the way in, not on the way out — a token that reaches the file has
 * already leaked. The patterns are deliberately conservative: they only touch
 * things that look like a credential, an email, an Indian phone number or a
 * long card-like number.
 */
object LogMasking {

    const val REDACTED = "[redacted]"

    private data class Rule(val regex: Regex, val replacement: String)

    private val rules: List<Rule> = listOf(
        // Whole-value headers first: "Authorization: Bearer xyz" would otherwise
        // only lose the word "Bearer" and keep the token.
        Rule(
            Regex("""(?i)\b(authorization|cookie|set-cookie|x-api-key)\b\s*[:=]\s*.*$"""),
            "$1: $REDACTED",
        ),
        Rule(Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/-]{8,}=*"""), "Bearer $REDACTED"),
        // key=value / key: value pairs whose key names a credential
        Rule(
            Regex(
                """(?i)\b(password|passwd|pwd|secret|token|access[_-]?token|refresh[_-]?token|""" +
                    """api[_-]?key|apikey|auth|signature|sig)\b""" +
                    """(\s*[=:]\s*)("[^"]*"|'[^']*'|\S+)""",
            ),
            "$1$2$REDACTED",
        ),
        // GitHub tokens
        Rule(Regex("""\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"""), REDACTED),
        // JWTs
        Rule(
            Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b"""),
            REDACTED,
        ),
        // Long hex / base64 blobs that are almost always keys
        Rule(Regex("""\b[A-Fa-f0-9]{40,}\b"""), REDACTED),
        // Email addresses
        Rule(Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""), REDACTED),
        // Indian mobile numbers, with or without +91 and separators
        Rule(Regex("""\b(?:\+?91[-\s]?)?[6-9]\d{9}\b"""), REDACTED),
        // Card-like digit runs
        Rule(Regex("""\b\d{13,19}\b"""), REDACTED),
    )

    /** Returns [input] with credentials and personal data replaced. */
    fun mask(input: String): String {
        if (input.isEmpty()) return input
        var out = input
        rules.forEach { rule -> out = rule.regex.replace(out, rule.replacement) }
        return out
    }

    /** True when the line contains something that was worth masking. */
    fun containsSecret(input: String): Boolean = mask(input) != input
}
