package net.hrapp.hr.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

object LocaleHelper {

    const val LANG_SYSTEM = "system"
    const val LANG_TR = "tr"
    const val LANG_EN = "en"

    /**
     * Desteklenen diller listesi
     */
    val supportedLanguages = listOf(
        LANG_SYSTEM to "Sistem Dili",
        LANG_TR to "Türkçe",
        LANG_EN to "English"
    )

    /**
     * Context'i belirtilen dil ile günceller
     */
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            LANG_SYSTEM -> getSystemLocale()
            else -> Locale(languageCode)
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Sistem dilini döndürür
     */
    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }

    /**
     * Dil kodundan görüntüleme adını döndürür
     */
    fun getLanguageDisplayName(code: String): String {
        return supportedLanguages.find { it.first == code }?.second ?: code
    }

    /**
     * Mevcut dil kodunu döndürür
     */
    fun getCurrentLanguageCode(context: Context): String {
        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return currentLocale.language
    }
}
