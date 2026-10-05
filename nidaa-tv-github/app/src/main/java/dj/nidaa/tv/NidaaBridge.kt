package dj.nidaa.tv

import android.os.Build
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Pont JavaScript exposé à la page sous `window.NidaaTV`.
 * Volontairement minimal : lecture d'informations non sensibles + rechargement.
 * Les méthodes sont appelées sur un thread du WebView, pas sur le thread UI.
 */
class NidaaBridge(private val onReload: () -> Unit) {

    @JavascriptInterface
    fun getVersion(): String = BuildConfig.VERSION_NAME

    /** Chaîne JSON : {"model","manufacturer","androidVersion","sdkInt","appVersion"}. */
    @JavascriptInterface
    fun getDeviceInfo(): String = JSONObject()
        .put("model", Build.MODEL ?: "")
        .put("manufacturer", Build.MANUFACTURER ?: "")
        .put("androidVersion", Build.VERSION.RELEASE ?: "")
        .put("sdkInt", Build.VERSION.SDK_INT)
        .put("appVersion", BuildConfig.VERSION_NAME)
        .toString()

    @JavascriptInterface
    fun reload() = onReload()

    companion object {
        const val NAME = "NidaaTV"
    }
}
