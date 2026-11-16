package com.example

import com.example.data.remote.extractFrappeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrappeErrorParserTest {

    @Test
    fun extractFrappeError_parsesServerMessagesHtml() {
        val body = """
            {
              "exc_type": "LinkValidationError",
              "_server_messages": "[\"{\\\"message\\\": \\\"Could not find <strong>Folder: Home/Call Recordings</strong>\\\"}\"]"
            }
        """.trimIndent()

        val result = extractFrappeError(417, body)
        assertEquals("HTTP 417: Could not find Folder: Home/Call Recordings", result)
    }

    @Test
    fun extractFrappeError_fallsBackToExcTypeOrMessage() {
        val body = """
            {
              "exc_type": "CSRFTokenError",
              "message": "Invalid Request"
            }
        """.trimIndent()

        val result = extractFrappeError(400, body)
        assertEquals("HTTP 400: CSRFTokenError", result)
    }

    @Test
    fun extractFrappeError_handlesBlankAndPlainTextBodies() {
        assertEquals("HTTP 502", extractFrappeError(502, null))
        assertEquals("HTTP 500: Internal Error", extractFrappeError(500, "Internal Error"))
        assertTrue(extractFrappeError(403, "{}").startsWith("HTTP 403"))
    }
}
