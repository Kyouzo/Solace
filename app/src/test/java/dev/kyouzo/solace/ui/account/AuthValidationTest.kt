package dev.kyouzo.solace.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.kyouzo.solace.R

class AuthValidationTest {
    @Test fun acceptsNormalEmails() {
        assertTrue(AuthValidation.isValidEmail("a@b.co"))
        assertTrue(AuthValidation.isValidEmail("  first.last+tag@example.com "))
    }

    @Test fun rejectsMalformedEmails() {
        listOf("", "plain", "a@b", "@b.com", "a b@c.com", "a@@b.com").forEach { assertFalse(it, AuthValidation.isValidEmail(it)) }
    }

    @Test fun passwordNeedsSixCharacters() {
        assertFalse(AuthValidation.isValidPassword("12345"))
        assertTrue(AuthValidation.isValidPassword("123456"))
    }

    @Test fun mapsKnownSupabaseErrors() {
        assertEquals(R.string.auth_error_rate_limit, AuthValidation.friendlyErrorRes("email rate limit exceeded"))
        assertEquals(R.string.auth_error_bad_credentials, AuthValidation.friendlyErrorRes("Invalid login credentials"))
        assertEquals(R.string.auth_error_exists, AuthValidation.friendlyErrorRes("User already registered"))
        assertEquals(R.string.auth_error_unconfirmed, AuthValidation.friendlyErrorRes("Email not confirmed"))
        assertEquals(R.string.auth_error_network, AuthValidation.friendlyErrorRes("Unable to resolve host \"x.supabase.co\""))
    }

    @Test fun unknownOrMissingErrorsHaveNoMapping() {
        assertNull(AuthValidation.friendlyErrorRes("something unexpected"))
        assertNull(AuthValidation.friendlyErrorRes(null))
        assertNotNull(AuthValidation.friendlyErrorRes("RATE LIMIT"))
    }
}
