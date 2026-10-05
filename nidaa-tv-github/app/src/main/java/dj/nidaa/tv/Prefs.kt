package dj.nidaa.tv

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/** Réglages persistants de l'appli (SharedPreferences). */
object Prefs {
    private const val FILE = "nidaa_tv"
    private const val KEY_URL = "screen_url"
    private const val KEY_AUTOSTART = "autostart"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** URL de l'écran web ; par défaut celle fixée à la compilation (-PnidaaScreenUrl). */
    // Décision de Med (2026-10) : aucun réglage sur l'écran, l'adresse est fixée à la compilation.
    fun screenUrl(@Suppress("UNUSED_PARAMETER") context: Context): String = BuildConfig.SCREEN_URL

    fun setScreenUrl(context: Context, url: String) {
        sp(context).edit().putString(KEY_URL, url).apply()
    }

    fun resetScreenUrl(context: Context) {
        sp(context).edit().remove(KEY_URL).apply()
    }

    /** Démarrage automatique au boot : activé par défaut. */
    fun autostart(context: Context): Boolean = sp(context).getBoolean(KEY_AUTOSTART, true)

    fun setAutostart(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTOSTART, enabled).apply()
    }

    fun isValidUrl(url: String): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        return (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
    }
}
