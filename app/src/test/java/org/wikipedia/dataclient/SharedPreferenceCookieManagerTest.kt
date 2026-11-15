package org.wikipedia.dataclient

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import okhttp3.Cookie
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.wikipedia.settings.Prefs

class SharedPreferenceCookieManagerTest {
    @Before
    fun setup() {
        mockkObject(Prefs)
        every { Prefs.cookies = any() } just Runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `all cookies are returned for the configured authority`() {
        val manager = SharedPreferenceCookieManager(
            mutableMapOf(CUSTOM_AUTHORITY to mutableListOf(
                cookie(CENTRALAUTH_TOKEN, CUSTOM_AUTHORITY),
                cookie(LOCAL_SESSION, CUSTOM_AUTHORITY)
            ))
        )

        val cookies = manager.loadForRequest("https://$CUSTOM_AUTHORITY/w/api.php")
        assertEquals(listOf(CENTRALAUTH_TOKEN, LOCAL_SESSION), cookies.map { it.name })
    }

    @Test
    fun `centralAuth cookies from Wikimedia are not transferred to the custom authority`() {
        val manager = SharedPreferenceCookieManager(
            mutableMapOf(COMMONS_AUTHORITY to mutableListOf(cookie(CENTRALAUTH_TOKEN, COMMONS_AUTHORITY)))
        )

        assertTrue(manager.loadForRequest("https://$CUSTOM_AUTHORITY/w/api.php").isEmpty())
    }

    @Test
    fun `expired cookies are removed from the jar when their authority is requested`() {
        val cookiesForCustomWiki = mutableListOf(
            expiredCookie(CENTRALAUTH_TOKEN, CUSTOM_AUTHORITY),
            cookie(CENTRALAUTH_USER, CUSTOM_AUTHORITY)
        )
        val manager = SharedPreferenceCookieManager(mutableMapOf(CUSTOM_AUTHORITY to cookiesForCustomWiki))

        val cookies = manager.loadForRequest("https://$CUSTOM_AUTHORITY/w/api.php")
        assertEquals(listOf(CENTRALAUTH_USER), cookies.map { it.name })
        assertEquals(listOf(CENTRALAUTH_USER), cookiesForCustomWiki.map { it.name })
    }

    @Test
    fun `cookies from an unrelated authority are not transferred`() {
        val manager = SharedPreferenceCookieManager(
            mutableMapOf("example.com" to mutableListOf(cookie(CENTRALAUTH_TOKEN, "example.com")))
        )

        assertTrue(manager.loadForRequest("https://$CUSTOM_AUTHORITY/w/api.php").isEmpty())
    }

    private fun cookie(name: String, domain: String): Cookie = Cookie.Builder()
        .name(name)
        .value("value_of_$name")
        .domain(domain)
        .path("/")
        .build()

    private fun expiredCookie(name: String, domain: String): Cookie = Cookie.Builder()
        .name(name)
        .value("value_of_$name")
        .domain(domain)
        .path("/")
        .expiresAt(System.currentTimeMillis() - 1000)
        .build()

    companion object {
        private const val CUSTOM_AUTHORITY = "wiki.skyblock.net"
        private const val COMMONS_AUTHORITY = "commons.wikimedia.org"
        private const val CENTRALAUTH_TOKEN = "centralauth_Token"
        private const val CENTRALAUTH_USER = "centralauth_User"
        private const val LOCAL_SESSION = "enwikiSession"
    }
}
