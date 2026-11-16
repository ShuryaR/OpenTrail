package com.example.util

/**
 * Strips spaces, dashes, parentheses, leading zeros, and common country prefixes (+91, 91, +1)
 * so incoming and outgoing cellular calls reliably match Frappe CRM records regardless of formatting.
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

    fun formatDisplay(raw: String?): String {
        if (raw.isNullOrBlank()) return "No phone"
        return raw.trim()
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}
