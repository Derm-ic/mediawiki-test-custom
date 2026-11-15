package org.wikipedia.dataclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wikipedia.json.JsonUtil
import org.wikipedia.test.TestParcelUtil

@RunWith(RobolectricTestRunner::class)
class WikiSiteTest {
    @Test
    fun testSupportedAuthority() {
        assertTrue(WikiSite.supportedAuthority(WikiSite.BASE_DOMAIN))
        assertTrue(WikiSite.supportedAuthority("www.${WikiSite.BASE_DOMAIN}"))
        assertFalse(WikiSite.supportedAuthority("en.wikipedia.org"))
        assertFalse(WikiSite.supportedAuthority("google.com"))
    }

    @Test
    fun testForLanguageCodeUsesSingleCustomAuthority() {
        val site = WikiSite.forLanguageCode("en")
        assertEquals("https", site.scheme())
        assertEquals(WikiSite.BASE_DOMAIN, site.authority())
        assertEquals("en", site.languageCode)
        assertEquals("", site.subdomain())
        assertEquals("skyblockwiki", site.dbName())
    }

    @Test
    fun testConstructorUsesSingleCustomAuthorityAndHttps() {
        val site = WikiSite("http://en.wikipedia.org/wiki/Foo")
        assertEquals("https", site.scheme())
        assertEquals(WikiSite.BASE_DOMAIN, site.authority())
        assertEquals("en", site.languageCode)
        assertEquals("https://${WikiSite.BASE_DOMAIN}", site.url())
    }

    @Test
    fun testLanguageCanBeReadFromCustomWikiPath() {
        val site = WikiSite("https://en.wikipedia.org/test/Foo")
        assertEquals(WikiSite.BASE_DOMAIN, site.authority())
        assertEquals("test", site.languageCode)
    }

    @Test
    fun testCustomPathAndUrl() {
        val site = WikiSite.forLanguageCode("en")
        assertEquals("/Segment", site.path("Segment"))
        assertEquals("/", site.path(""))
        assertEquals("https://${WikiSite.BASE_DOMAIN}/Segment", site.url("Segment"))
    }

    @Test
    fun testConstructorLanguageOverride() {
        val site = WikiSite("en.wikipedia.org", "test")
        assertEquals(WikiSite.BASE_DOMAIN, site.authority())
        assertEquals("test", site.languageCode)
    }

    @Test
    fun testParcelAndSerialization() {
        val site = WikiSite.forLanguageCode("en")
        TestParcelUtil.test(site)
        val decoded = JsonUtil.decodeFromString<WikiSite>(JsonUtil.encodeToString(site))!!
        assertEquals(site, decoded)
    }
}
