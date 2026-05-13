package com.randomchat.shnapp.utils

/**
 * Detects user-shared personal information (phone, email, social handles) in a message.
 *
 * Client-side first-line defense — catches casual sharing without server cost.
 * Normalises common obfuscation: fullwidth digits, word-numbers, zero-width chars,
 * "(at)"/"(dot)" tricks. Not bullet-proof; server-side enforcement is the
 * authoritative layer (Cloud Function on RTDB write).
 *
 * Design notes:
 *  • Phone threshold = 7 consecutive digits — avoids "I'm 25" / "I have 3 brothers".
 *  • Email regex tolerates `name (at) domain (dot) com`.
 *  • Social regex requires platform keyword + handle (≥3 chars after) to avoid
 *    matching every "@mention".
 */
object PiiDetector {

    enum class Kind { PHONE, EMAIL, SOCIAL_HANDLE }

    private val numberWords = mapOf(
        "zero"  to "0", "one"   to "1", "two"   to "2", "three" to "3", "four" to "4",
        "five"  to "5", "six"   to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten"   to "10", "oh"   to "0", "o"     to "0"
    )

    // ≥7 consecutive digits with optional separators (space, dash, dot, parens, plus)
    private val phoneRegex = Regex("""(?:\+?\d[\s\-.()]{0,2}){7,15}""")

    // Email with obfuscation tolerance: "(at)" "[at]" " at "  and same for "."
    private val emailRegex = Regex(
        """[a-z0-9._%+\-]+\s*(?:@|\(at\)|\[at\]|\sat\s)\s*[a-z0-9.\-]+\s*(?:\.|\(dot\)|\[dot\]|\sdot\s)\s*[a-z]{2,}""",
        RegexOption.IGNORE_CASE
    )

    // Social platform name immediately followed by handle/url (≥3 non-space chars).
    private val socialPlatformRegex = Regex(
        """\b(?:insta(?:gram)?|snap(?:chat)?|t(?:ele)?g(?:ram)?|whats?app|wa|discord|fb|facebook|messenger|kik|skype|signal|tiktok|tt|line|viber|wechat)\b\s*[:=\-]?\s*\S{3,}""",
        RegexOption.IGNORE_CASE
    )

    // "Add me on X", "find me at Y", "hit me up on Z"
    private val contactInviteRegex = Regex(
        """\b(?:add|find|hit|dm|message|text|call|ping)\s+me\s+(?:on|at|via|@)\s+\S+""",
        RegexOption.IGNORE_CASE
    )

    /** True if message appears to contain personal contact info. */
    fun containsPii(text: String): Boolean = detect(text) != null

    /** Returns the first detected kind, or null. Useful for tailored messaging. */
    fun detect(text: String): Kind? {
        if (text.isBlank()) return null
        val norm = normalize(text)
        return when {
            phoneRegex.containsMatchIn(norm)           -> Kind.PHONE
            emailRegex.containsMatchIn(norm)           -> Kind.EMAIL
            socialPlatformRegex.containsMatchIn(norm)  -> Kind.SOCIAL_HANDLE
            contactInviteRegex.containsMatchIn(norm)   -> Kind.SOCIAL_HANDLE
            else                                       -> null
        }
    }

    /**
     * Lowercase, strip zero-width / bidi marks, fold any Unicode digit to ASCII,
     * replace word-numbers, collapse whitespace.
     */
    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            // Strip zero-width / bidi marks
            val code = ch.code
            if (code in 0x200B..0x200F || code in 0x202A..0x202E || code == 0xFEFF) continue
            // Fold Unicode digits (fullwidth/math/etc.) to ASCII 0-9
            val d = Character.digit(ch, 10)
            if (d in 0..9) { sb.append(d); continue }
            sb.append(ch.lowercaseChar())
        }
        var out = sb.toString()
        // Replace number-word obfuscation with digits (whole-word boundaries only)
        for ((word, digit) in numberWords) {
            out = out.replace(Regex("\\b$word\\b"), digit)
        }
        // Collapse whitespace
        return out.replace(Regex("\\s+"), " ").trim()
    }
}
