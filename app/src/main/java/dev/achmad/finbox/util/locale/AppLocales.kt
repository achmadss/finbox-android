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
 * The languages this build ships. The build writes the locale config from the
 * `values-*` folders, so adding `values-in/strings.xml` is all it takes for a
 * language to appear in both the app's language screen and the system's.
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
 * The language the app is currently showing. A language picked in the system's
 * per-app language screen counts; one with no translation reads as English.
 */
fun currentLanguage(context: Context): String {
    val chosen = AppCompatDelegate.getApplicationLocales()
        .takeIf { !it.isEmpty }
        ?.get(0)
        ?.language
        ?: Locale.getDefault().language
    return supportedLanguages(context).firstOrNull { it == chosen } ?: DEFAULT_LANGUAGE
}

/** Applies [language] app-wide; Android keeps it across restarts. */
fun setLanguage(language: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
}

/** A language's name in that language: `English`, `Bahasa Indonesia`. */
fun displayName(language: String): String {
    val locale = Locale.forLanguageTag(language)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * The AGP-generated locale config, for Android versions without [LocaleConfig].
 * A build that stops generating it fails here rather than offering no languages.
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
