package org.wikipedia.page

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wikipedia.dataclient.WikiSite

@RunWith(RobolectricTestRunner::class)
class PageTitleTest {
    @Test
    fun testCanonicalURLUsesConfiguredWikiHost() {
        val site = WikiSite.forLanguageCode("en")
        assertEquals("https://${WikiSite.BASE_DOMAIN}/wiki/India", PageTitle.titleForInternalLink("/wiki/India", site).uri)
        assertEquals("https://${WikiSite.BASE_DOMAIN}/wiki/India_Gate", PageTitle.titleForInternalLink("/wiki/India Gate", site).uri)
        assertEquals("https://${WikiSite.BASE_DOMAIN}/wiki/India%27s_Gate", PageTitle.titleForInternalLink("/wiki/India's Gate", site).uri)
    }

    @Test
    fun testLanguageVariantsUseConfiguredWikiHost() {
        for (languageCode in listOf("en", "zh", "zh-tw", "zh-cn", "zh-classical", "zh-min-nan", "zh-yue")) {
            val site = WikiSite.forLanguageCode(languageCode)
            assertEquals("https://${WikiSite.BASE_DOMAIN}/wiki/Taiwan", PageTitle("Taiwan", site).uri)
        }
    }

    @Test
    fun testLanguagePrefixKeepsConfiguredWikiHost() {
        val uri = Uri.parse("https://wiki.skyblock.net/wiki/fr:Article")
        val title = PageTitle.titleForUri(uri, WikiSite(uri))
        assertEquals(WikiSite.BASE_DOMAIN, title.wikiSite.authority())
        assertEquals("fr", title.wikiSite.languageCode)
        assertEquals("Article", title.displayText)
    }
}
