package com.pocketaction.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidationTest {
    @Test fun addsHttpsToBareWebHost() {
        assertEquals("https://example.com/path", InputValidation.normalizeHttpUrl("example.com/path"))
    }

    @Test fun acceptsHttpAndHttps() {
        assertEquals("https://example.com", InputValidation.normalizeHttpUrl("https://example.com"))
        assertEquals("http://example.com", InputValidation.normalizeHttpUrl("http://example.com"))
    }

    @Test fun rejectsUnsupportedSchemes() {
        assertNull(InputValidation.normalizeHttpUrl("javascript:alert(1)"))
        assertNull(InputValidation.normalizeHttpUrl("file:///etc/passwd"))
        assertNull(InputValidation.normalizeHttpUrl("intent://example.com"))
    }

    @Test fun rejectsMissingHostAndEmbeddedCredentials() {
        assertNull(InputValidation.normalizeHttpUrl("https://"))
        assertNull(InputValidation.normalizeHttpUrl("https://user:pass@example.com"))
    }
}
