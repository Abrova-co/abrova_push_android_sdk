package ir.abrova.push.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Tokens and passwords never reach the log. */
class LogMaskTest {
    @Test fun `masks token, password and secret values, keeps the rest`() {
        val body = """{"subscriptionId":"s-1","mqtt":{"host":"pn.example","port":8883,"token":"eyJhbGciOi.secret.part","password":"p4ss"},"userToken":"abc","clientSecret":"x"}"""
        val masked = LogMask.secrets(body)
        assertEquals(
            """{"subscriptionId":"s-1","mqtt":{"host":"pn.example","port":8883,"token":"***","password":"***"},"userToken":"***","clientSecret":"***"}""",
            masked,
        )
        assertFalse(masked.contains("eyJhbGciOi"))
        assertEquals("", LogMask.secrets(null))
    }
}
