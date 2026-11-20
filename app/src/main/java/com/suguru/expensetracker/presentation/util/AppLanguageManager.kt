package com.suguru.expensetracker.presentation.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Installation-wide app locale, deliberately stored outside workspace and UID preferences.
 */
object AppLanguageManager {
    private const val PREFS = "app_language"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    private val supportedTags = setOf("uz", "en", "ru")
    private val _languageChanges = MutableStateFlow<String?>(null)
    val languageChanges: StateFlow<String?> = _languageChanges.asStateFlow()

    fun selectedLanguageTag(context: Context): String {
        val appContext = context.applicationContext ?: context
        val saved = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)
        return saved?.takeIf { it in supportedTags } ?: deviceLanguageTag(context)
    }

    fun setLanguage(context: Context, languageTag: String) {
        require(languageTag in supportedTags) { "Unsupported app language" }
        val appContext = context.applicationContext ?: context
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
        applyDefaultLocale(languageTag)
        _languageChanges.value = languageTag
    }

    fun applySavedLocale(context: Context): String {
        val languageTag = selectedLanguageTag(context)
        applyDefaultLocale(languageTag)
        return languageTag
    }

    fun wrap(context: Context): Context = wrap(context, selectedLanguageTag(context))

    fun wrap(context: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        applyDefaultLocale(languageTag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        val localizedContext = context.createConfigurationContext(configuration)
        return object : ContextWrapper(context) {
            override fun getResources() = localizedContext.resources
            override fun getAssets() = localizedContext.assets
        }
    }

    private fun applyDefaultLocale(languageTag: String) {
        Locale.setDefault(Locale.forLanguageTag(languageTag))
    }

    private fun deviceLanguageTag(context: Context): String {
        val deviceTag = if (android.os.Build.VERSION.SDK_INT >= 24) {
            context.resources.configuration.locales[0]?.language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale?.language
        }
        return deviceTag?.takeIf { it in supportedTags } ?: "en"
    }
}
