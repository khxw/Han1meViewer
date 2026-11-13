package com.yenaly.han1meviewer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiesTest {

    @Test
    fun extractsNamedCookieFromHeader() {
        val header = "__cf_bm=abc; cf_clearance=clearance-value; user_lang=zh"
        assertEquals("clearance-value", header.cookieValue("cf_clearance"))
    }

    @Test
    fun missingOrBlankCookiesReturnNull() {
        assertNull("__cf_bm=abc".cookieValue("cf_clearance"))
        assertNull("cf_clearance=   ".cookieValue("cf_clearance"))
        assertNull(null.cookieValue("cf_clearance"))
    }

    @Test
    fun detectsHanimeLoginSessionCookies() {
        assertTrue("XSRF-TOKEN=x; hanime1_session=session-value".hasHanimeLoginSession())
        assertTrue("laravel_session=session-value".hasHanimeLoginSession())
        assertTrue(
            "remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=value"
                .hasHanimeLoginSession()
        )
    }

    @Test
    fun xsrfCookieAloneIsNotLoginSession() {
        assertFalse("XSRF-TOKEN=x; __cf_bm=y".hasHanimeLoginSession())
        assertFalse(null.hasHanimeLoginSession())
    }
}
