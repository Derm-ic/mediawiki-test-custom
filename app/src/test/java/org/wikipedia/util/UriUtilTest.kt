    @Test
    fun testIsAppSupportedLink() {
        assertTrue(UriUtil.isAppSupportedLink(Uri.parse("https://wiki.skyblock.net/wiki/Obama_Barack?markasread=10520073")))
        assertTrue(UriUtil.isAppSupportedLink(Uri.parse("https://wiki.skyblock.net/w/index.php?title=Spacetime&diff=816093705")))
        assertFalse(UriUtil.isAppSupportedLink(Uri.parse("https://en.wikipedia.org/wiki/Obama_Barack")))
        assertFalse(UriUtil.isAppSupportedLink(Uri.parse("https://commons.wikimedia.org/wiki/User_talk:Cooltey")))
        assertFalse(UriUtil.isAppSupportedLink(Uri.parse("https://mediawiki.org/wiki/Help:Login_notifications")))
    }
