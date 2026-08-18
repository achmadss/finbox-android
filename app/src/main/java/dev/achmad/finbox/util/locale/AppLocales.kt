package dev.achmad.finbox.util.locale

import android.app.LocaleConfig
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.achmad.finbox.R
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

/**
 * The languages this build ships, and which one it is speaking.
 *
 * Nothing here is a list to maintain: the build writes the locale config from
 * the `values-*` folders, so adding `values-in/strings.xml` is all it takes for
 * Indonesian to appear in both the app's language screen and the system's.
 */

/** What an unsupported or unset language falls back to — the `values/` folder. */
const val DEFAULT_LANGUAGE = "en"

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** Language tags this build has resources for, the default first. */
fun supportedLanguages(context: Context): List<String> {
    val tags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // The platform reads the same config out of the manifest.
        val locales = LocaleConfig(context).supportedLocales
        (0 until (locales?.size() ?: 0)).mapNotNull { locales?.get(it)?.toLanguageTag() }
    } else {
        readLocaleConfig(context)
    }
    return tags
        .map { Locale.forLanguageTag(it).language }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { if (it == DEFAULT_LANGUAGE) "" else displayName(it) }
}

/**
 * The language the app is currently showing.
 *
 * A language picked outside the app — the system's per-app language screen —
 * counts, and one this build has no translation for reads as English, since
 * that is what the user is actually looking at.
 */
fun currentLanguage(context: Context): String {
    val chosen = AppCompatDelegate.getApplicationLocales()
        .takeIf { !it.isEmpty }
        ?.get(0)
        ?.language
        ?: Locale.getDefault().language
    return supportedLanguages(context).firstOrNull { it == chosen } ?: DEFAULT_LANGUAGE
}

/** Applies [language] app-wide. Android remembers it across restarts. */
fun setLanguage(language: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
}

/** A language's name in that language: `English`, `Bahasa Indonesia`. */
fun displayName(language: String): String {
    val locale = Locale.forLanguageTag(language)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * Reads the locale config the build generated, for Android versions without
 * [LocaleConfig]. The resource is AGP's; a build that stops generating it fails
 * here rather than silently offering no languages.
 */
private fun readLocaleConfig(context: Context): List<String> {
    val parser = context.resources.getXml(R.xml._generated_res_locale_config)
    return buildList {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")?.let(::add)
            }
        }
    }
}
