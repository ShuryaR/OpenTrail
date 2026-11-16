package com.example.util

/**
 * Strips spaces, dashes, parentheses, leading zeros, and common country prefixes (+91, 91, +1)
 * so incoming and outgoing cellular calls reliably match Frappe CRM records regardless of formatting.
 * Also provides Miller's Law chunked phone formatting and liberal dialer input sanitization (Postel's Law).
 */
object PhoneNormalizer {

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digitsOnly = raw.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""

        // Strip leading 00 international exit code or single leading 0
        var trimmed = digitsOnly
        if (trimmed.startsWith("00")) {
            trimmed = trimmed.drop(2)
        }
        while (trimmed.startsWith("0") && trimmed.length > 10) {
            trimmed = trimmed.drop(1)
        }

        // Strip +91 / 91 for Indian 10-digit numbers, or +1 / 1 for US 10-digit numbers
        if (trimmed.length == 12 && trimmed.startsWith("91")) {
            trimmed = trimmed.drop(2)
        } else if (trimmed.length == 11 && trimmed.startsWith("1")) {
            trimmed = trimmed.drop(1)
        } else if (trimmed.length == 11 && trimmed.startsWith("0")) {
            trimmed = trimmed.drop(1)
        }

        // Canonical matching suffix (last 10 digits when >= 10 digits)
        return if (trimmed.length > 10) trimmed.takeLast(10) else trimmed
    }

    /**
     * Formats phone numbers into readable chunks (Miller's Law) while tolerating any input (Postel's Law).
     */
    fun formatDisplay(raw: String?): String {
        if (raw.isNullOrBlank()) return "No phone"
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return trimmed

        return when {
            digits.length == 12 && digits.startsWith("91") -> {
                val local = digits.drop(2)
                "+91 ${local.substring(0, 5)} ${local.substring(5)}"
            }
            digits.length == 11 && digits.startsWith("1") -> {
                val local = digits.drop(1)
                "+1 (${local.substring(0, 3)}) ${local.substring(3, 6)}-${local.substring(6)}"
            }
            digits.length == 10 && !hasPlus -> {
                if (digits.first() in '6'..'9') {
                    "${digits.substring(0, 5)} ${digits.substring(5)}"
                } else {
                    "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
                }
            }
            digits.length > 6 -> {
                val prefix = if (hasPlus) "+" else ""
                prefix + digits.chunked(4).joinToString(" ")
            }
            else -> trimmed
        }
    }

    /**
     * Accepts messy pasted numbers (spaces, dashes, brackets) and retains valid dialer characters.
     */
    fun sanitizeDialInput(raw: String): String {
        val sb = StringBuilder()
        raw.forEach { ch ->
            when {
                ch.isDigit() || ch == '*' || ch == '#' -> sb.append(ch)
                ch == '+' && sb.isEmpty() -> sb.append(ch)
            }
        }
        return sb.toString().take(20)
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}
