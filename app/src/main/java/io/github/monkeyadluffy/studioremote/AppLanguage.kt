package io.github.monkeyadluffy.studioremote

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/**
 * The display language picked in Settings → Appearance, as in Studio's web settings. Kept in
 * SharedPreferences because it has to be read synchronously, before the first screen is drawn.
 * Every context that shows text (activity, service, view model) goes through [wrap].
 */
object AppLanguage {
    /** Follow the phone's language, falling back to English when there is no translation. */
    const val AUTO = "auto"
    private const val DEFAULT = "en"
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    /** Shipped translations, shown by their own name like Studio's language picker. */
    private val nativeNames = linkedMapOf("en" to "English", "tr" to "Türkçe")

    val choices: List<String> = listOf(AUTO) + nativeNames.keys

    fun nativeName(tag: String): String = nativeNames[tag] ?: tag

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    /** Saves [tag]; screens pick it up once the activity is recreated. */
    fun set(context: Context, tag: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, tag) }

    /** [base] with its resources switched to the chosen language. */
    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag == AUTO) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }
}
