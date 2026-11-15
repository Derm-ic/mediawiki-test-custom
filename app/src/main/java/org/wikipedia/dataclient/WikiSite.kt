/** package org.wikipedia.dataclient

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.wikipedia.Constants
import org.wikipedia.WikipediaApp
import org.wikipedia.json.UriSerializer
import org.wikipedia.language.AppLanguageLookUpTable
import org.wikipedia.util.UriUtil

/**
 * The base URL and Wikipedia language code for a MediaWiki site. Examples:
 *
 *
 * <lh>Name: scheme / authority / language code</lh>
 *  * English Wikipedia: HTTPS / en.wikipedia.org / en
 *  * Chinese Wikipedia: HTTPS / zh.wikipedia.org / zh-hans or zh-hant
 *  * Meta-Wiki: HTTPS / meta.wikimedia.org / (none)
 *  * Test Wikipedia: HTTPS / test.wikipedia.org / test
 *  * Võro Wikipedia: HTTPS / fiu-vro.wikipedia.org / fiu-vro
 *  * Simple English Wikipedia: HTTPS / simple.wikipedia.org / simple
 *  * Simple English Wikipedia (beta cluster mirror): HTTP / simple.wikipedia.beta.wmflabs.org / simple
 *  * Development: HTTP / 192.168.1.11:8080 / (none)
 *
 *
 * **As shown above, the language code or mapping is part of the authority:**
 *
 * <lh>Validity: authority / language code</lh>
 *  * Correct: "test.wikipedia.org" / "test"
 *  * Correct: "wikipedia.org", ""
 *  * Correct: "no.wikipedia.org", "nb"
 *  * Incorrect: "wikipedia.org", "test"
 *
 */
@Serializable
@Parcelize
data class WikiSite(
    @SerialName("domain") @Serializable(with = UriSerializer::class) var uri: Uri,
    var languageCode: String = ""
) : Parcelable {

    constructor(uri: Uri) : this(uri, "") {
        val tempUri = ensureScheme(uri)
        var authority = tempUri.authority.orEmpty()
        if ((BASE_DOMAIN == authority || ("www.$BASE_DOMAIN") == authority) &&
            tempUri.path?.startsWith("/wiki") == true
        ) {
            // Special case for Wikipedia only: assume English subdomain when none given.
            authority = "en.$BASE_DOMAIN"
        }

        // Unconditionally transform any mobile authority to canonical.
        authority = authority.replace(".m.", ".")
        languageCode = UriUtil.getLanguageVariantFromUri(tempUri).ifEmpty { authorityToLanguageCode(authority) }

        // For language variant wikis, automatically switch to the preferred variant if possible.
        val parentLanguageCode = WikipediaApp.instance.languageState.getDefaultLanguageCode(languageCode)
        // For the default language code like "zh", we need to check if it has variants.
        var languageVariants = WikipediaApp.instance.languageState.getLanguageVariants(languageCode)
        if (parentLanguageCode != null || languageVariants != null) {
            // Get language variants from the parent language code
            if (languageVariants == null) {
                languageVariants = WikipediaApp.instance.languageState.getLanguageVariants(parentLanguageCode)
            }
            // Try to find the first selected variant that matches the URL's parent language code
            // This prevents showing mixed language variants article when the URL contains parent language codes such as "zh" or "wiki"
            languageCode = WikipediaApp.instance.languageState.appLanguageCodes.firstOrNull {
                languageVariants?.contains(it) == true
            } ?: languageCode
        }

        if (languageCode == Constants.WIKI_CODE_COMMONS) {
            // Special case for Commons: if the WikiSite was constructed from "commons.wikimedia.org",
            // then the languageCode will be "commons" which is incorrect, so set it to the default language.
            languageCode = WikipediaApp.instance.appOrSystemLanguageCode
        }

        // Use default subdomain in authority to prevent error when requesting endpoints. e.g. zh-tw.wikipedia.org
        if (authority.contains(BASE_DOMAIN) && subdomain().isNotEmpty()) {
            authority = subdomain() + "." + BASE_DOMAIN
        }
        this.uri = Uri.Builder().scheme(tempUri.scheme).encodedAuthority(authority).build()
    }

    constructor(url: String) : this(
        when {
            url.startsWith("http") -> url.toUri()
            url.startsWith("//") -> "$DEFAULT_SCHEME:$url".toUri()
            else -> "$DEFAULT_SCHEME://$url".toUri()
        }
    )

    constructor(authority: String, languageCode: String) : this(authority) {
        this.languageCode = languageCode
    }

    fun scheme(): String {
        return uri.scheme.orEmpty().ifEmpty { DEFAULT_SCHEME }
    }

    fun authority(): String {
        return uri.authority.orEmpty()
    }

    fun subdomain(): String {
        return languageCodeToSubdomain(languageCode)
    }

    fun path(segment: String): String {
        return "/w/$segment"
    }

    fun url(): String {
        return uri.toString()
    }

    fun url(segment: String): String {
        return url() + path(segment)
    }

    fun dbName(): String {
        return (if (uri.authority.orEmpty().contains("wikidata")) {
            "wikidata"
        } else if (uri.authority.orEmpty().contains("commons")) {
            "commons"
        } else {
            subdomain().replace("-".toRegex(), "_")
        }) + "wiki"
    }

    companion object {
        const val DEFAULT_SCHEME = "https"
        const val BASE_DOMAIN = "wikipedia.org"
        private var DEFAULT_BASE_URL: String? = null

        fun supportedAuthority(authority: String): Boolean {
            return Service.matchesAuthority(authority, DEFAULT_BASE_URL!!.toUri().authority!!)
        }

        fun setDefaultBaseUrl(url: String) {
            DEFAULT_BASE_URL = url.ifEmpty { Service.WIKIPEDIA_URL }
        }

        fun forLanguageCode(languageCode: String): WikiSite {
            val uri = ensureScheme(DEFAULT_BASE_URL!!.toUri())
            return WikiSite(
                (if (languageCode.isEmpty()) "" else languageCodeToSubdomain(languageCode) + ".") + uri.authority,
                languageCode
            )
        }

        fun normalizeLanguageCode(languageCode: String): String {
            return when (languageCode) {
                AppLanguageLookUpTable.NORWEGIAN_BOKMAL_LANGUAGE_CODE -> AppLanguageLookUpTable.NORWEGIAN_LEGACY_LANGUAGE_CODE // T114042
                AppLanguageLookUpTable.BELARUSIAN_LEGACY_LANGUAGE_CODE -> AppLanguageLookUpTable.BELARUSIAN_TARASK_LANGUAGE_CODE // T111853
                AppLanguageLookUpTable.CHINESE_LEGACY_YUE_LANGUAGE_CODE -> AppLanguageLookUpTable.CHINESE_YUE_LANGUAGE_CODE
                AppLanguageLookUpTable.CHINESE_CLASSICAL_LANGUAGE_CODE -> AppLanguageLookUpTable.CHINESE_CLASSICAL_LEGACY_LANGUAGE_CODE
                AppLanguageLookUpTable.CHINESE_MIN_NAN_LANGUAGE_CODE -> AppLanguageLookUpTable.CHINESE_MIN_NAN_LEGACY_LANGUAGE_CODE
                else -> languageCode
            }
        }

        /**
         * For use only in Composable Previews, since this bypasses much of the internal constructor
         * logic that depends on a WikipediaApp instance.
         */
        fun preview(languageCode: String = "en"): WikiSite {
            return WikiSite("https://$languageCode.wikipedia.org/".toUri(), languageCode)
        }

        private fun languageCodeToSubdomain(languageCode: String): String {
            return WikipediaApp.instance.languageState.getDefaultLanguageCode(languageCode) ?: normalizeLanguageCode(languageCode)
        }

        fun authorityToLanguageCode(authority: String): String {
            val parts = authority.split("\\.".toRegex()).toTypedArray()
            val minLengthForSubdomain = 3
            return if (parts.size < minLengthForSubdomain ||
                parts.size == minLengthForSubdomain && parts[0] == "m"
            ) {
                // ""
                // wikipedia.org
                // m.wikipedia.org
                ""
            } else parts[0]
        }

        private fun ensureScheme(uri: Uri): Uri {
            return if (uri.scheme.isNullOrEmpty()) {
                uri.buildUpon().scheme(DEFAULT_SCHEME).build()
            } else uri
        }
    }
}
*/

package org.wikipedia.dataclient

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.wikipedia.Constants
import org.wikipedia.WikipediaApp
import org.wikipedia.json.UriSerializer
import org.wikipedia.language.AppLanguageLookUpTable
import org.wikipedia.util.UriUtil

/**
 * The base URL and language code for a MediaWiki site.
 *
 * Customized build: every WikiSite resolves to a single host (BASE_DOMAIN),
 * regardless of the host passed in (Wikipedia, Wikidata, Commons, etc.).
 * The wiki is served from the web root, so there is no "/w/" script path.
 */
@Serializable
@Parcelize
data class WikiSite(
    @SerialName("domain") @Serializable(with = UriSerializer::class) var uri: Uri,
    var languageCode: String = ""
) : Parcelable {

    constructor(uri: Uri) : this(uri, "") {
        val tempUri = ensureScheme(uri)

        // CUSTOM: always use our single host, whatever host was passed in.
        val authority = BASE_DOMAIN

        languageCode = UriUtil.getLanguageVariantFromUri(tempUri).ifEmpty { authorityToLanguageCode(authority) }

        // For language variant wikis, automatically switch to the preferred variant if possible.
        val parentLanguageCode = WikipediaApp.instance.languageState.getDefaultLanguageCode(languageCode)
        // For the default language code like "zh", we need to check if it has variants.
        var languageVariants = WikipediaApp.instance.languageState.getLanguageVariants(languageCode)
        if (parentLanguageCode != null || languageVariants != null) {
            // Get language variants from the parent language code
            if (languageVariants == null) {
                languageVariants = WikipediaApp.instance.languageState.getLanguageVariants(parentLanguageCode)
            }
            // Try to find the first selected variant that matches the URL's parent language code
            // This prevents showing mixed language variants article when the URL contains parent language codes such as "zh" or "wiki"
            languageCode = WikipediaApp.instance.languageState.appLanguageCodes.firstOrNull {
                languageVariants?.contains(it) == true
            } ?: languageCode
        }

        if (languageCode == Constants.WIKI_CODE_COMMONS) {
            // Special case for Commons: if the WikiSite was constructed from "commons.wikimedia.org",
            // then the languageCode will be "commons" which is incorrect, so set it to the default language.
            languageCode = WikipediaApp.instance.appOrSystemLanguageCode
        }

        // CUSTOM: our wiki has no language subdomains, so fall back to English if nothing was resolved.
        if (languageCode.isEmpty()) {
            languageCode = DEFAULT_LANGUAGE_CODE
        }

        // CUSTOM: no subdomain rewriting; scheme is always https.
        this.uri = Uri.Builder().scheme(DEFAULT_SCHEME).encodedAuthority(authority).build()
    }

    constructor(url: String) : this(
        when {
            url.startsWith("http") -> url.toUri()
            url.startsWith("//") -> "$DEFAULT_SCHEME:$url".toUri()
            else -> "$DEFAULT_SCHEME://$url".toUri()
        }
    )

    constructor(authority: String, languageCode: String) : this(authority) {
        this.languageCode = languageCode
    }

    fun scheme(): String {
        return uri.scheme.orEmpty().ifEmpty { DEFAULT_SCHEME }
    }

    fun authority(): String {
        return uri.authority.orEmpty()
    }

    fun subdomain(): String {
        // CUSTOM: no language subdomains on our wiki.
        return ""
    }

    fun path(segment: String): String {
        // CUSTOM: was "/w/$segment". Our wiki serves api.php, index.php etc. from the root.
        return "/$segment"
    }

    fun url(): String {
        return uri.toString()
    }

    fun url(segment: String): String {
        return url() + path(segment)
    }

    fun dbName(): String {
        // CUSTOM: single wiki.
        return "skyblockwiki"
    }

    companion object {
        const val DEFAULT_SCHEME = "https"
        const val BASE_DOMAIN = "wiki.skyblock.net"
        const val DEFAULT_LANGUAGE_CODE = "en"
        private var DEFAULT_BASE_URL: String = "$DEFAULT_SCHEME://$BASE_DOMAIN"

        fun supportedAuthority(authority: String): Boolean {
            return authority == BASE_DOMAIN || authority == "www.$BASE_DOMAIN"
        }

        fun setDefaultBaseUrl(url: String) {
            // CUSTOM: was url.ifEmpty { Service.WIKIPEDIA_URL }. Always keep our own URL.
            DEFAULT_BASE_URL = "$DEFAULT_SCHEME://$BASE_DOMAIN"
        }

        fun forLanguageCode(languageCode: String): WikiSite {
            // CUSTOM: no "<lang>." prefix; same host for every language.
            return WikiSite(BASE_DOMAIN, languageCode)
        }

        fun normalizeLanguageCode(languageCode: String): String {
            return when (languageCode) {
                AppLanguageLookUpTable.NORWEGIAN_BOKMAL_LANGUAGE_CODE -> AppLanguageLookUpTable.NORWEGIAN_LEGACY_LANGUAGE_CODE // T114042
                AppLanguageLookUpTable.BELARUSIAN_LEGACY_LANGUAGE_CODE -> AppLanguageLookUpTable.BELARUSIAN_TARASK_LANGUAGE_CODE // T111853
                AppLanguageLookUpTable.CHINESE_LEGACY_YUE_LANGUAGE_CODE -> AppLanguageLookUpTable.CHINESE_YUE_LANGUAGE_CODE
                AppLanguageLookUpTable.CHINESE_CLASSICAL_LANGUAGE_CODE -> AppLanguageLookUpTable.CHINESE_CLASSICAL_LEGACY_LANGUAGE_CODE
                AppLanguageLookUpTable.CHINESE_MIN_NAN_LANGUAGE_CODE -> AppLanguageLookUpTable.CHINESE_MIN_NAN_LEGACY_LANGUAGE_CODE
                else -> languageCode
            }
        }

        /**
         * For use only in Composable Previews, since this bypasses much of the internal constructor
         * logic that depends on a WikipediaApp instance.
         */
        fun preview(languageCode: String = "en"): WikiSite {
            return WikiSite("$DEFAULT_SCHEME://$BASE_DOMAIN/".toUri(), languageCode)
        }

        private fun languageCodeToSubdomain(languageCode: String): String {
            return WikipediaApp.instance.languageState.getDefaultLanguageCode(languageCode) ?: normalizeLanguageCode(languageCode)
        }

        fun authorityToLanguageCode(authority: String): String {
            // CUSTOM: our host (wiki.skyblock.net) has 3 parts, so the original logic would
            // wrongly return "wiki" as a language code. Return empty; the constructor defaults it to "en".
            if (authority == BASE_DOMAIN || authority == "www.$BASE_DOMAIN") {
                return ""
            }
            val parts = authority.split("\\.".toRegex()).toTypedArray()
            val minLengthForSubdomain = 3
            return if (parts.size < minLengthForSubdomain ||
                parts.size == minLengthForSubdomain && parts[0] == "m"
            ) {
                ""
            } else parts[0]
        }

        private fun ensureScheme(uri: Uri): Uri {
            return if (uri.scheme.isNullOrEmpty()) {
                uri.buildUpon().scheme(DEFAULT_SCHEME).build()
            } else uri
        }
    }
}
