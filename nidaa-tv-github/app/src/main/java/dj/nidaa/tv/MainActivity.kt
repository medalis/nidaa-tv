package dj.nidaa.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Activité unique : l'écran web Nidaa (PWA) dans un WebView plein écran, en mode kiosque.
 *
 * Raison d'être de l'enveloppe native : autoriser la lecture audio sans geste utilisateur
 * (son de l'adhan), garder l'écran allumé, démarrer au boot et verrouiller la télécommande.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webContainer: FrameLayout
    private lateinit var fallback: View
    private lateinit var fallbackUrl: TextView
    private var webView: WebView? = null
    private val settingsDialog by lazy { SettingsDialog(this) }

    /** Vrai tant que le dernier chargement de la page principale a échoué. */
    private var pageFailed = false

    /** Erreur vue pendant le chargement en cours (remise à zéro à chaque onPageStarted). */
    private var loadError = false

    private val backPresses = ArrayDeque<Long>()
    private var okLongPressFired = false

    private val retryRunnable = Runnable { if (pageFailed) loadScreen() }
    private val okLongPressRunnable = Runnable {
        okLongPressFired = true
        openSettings()
    }
    private val unlockAudioRunnable = object : Runnable {
        override fun run() {
            webView?.evaluateJavascript(UNLOCK_AUDIO_JS, null)
            handler.postDelayed(this, UNLOCK_AUDIO_PERIOD_MS)
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ------------------------------------------------------------------ cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        setContentView(R.layout.activity_main)
        webContainer = findViewById(R.id.web_container)
        fallback = findViewById(R.id.fallback)
        fallbackUrl = findViewById(R.id.fallback_url)

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        applyImmersive()
        registerNetworkCallback()
        loadScreen()
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        settingsDialog.dismiss()
        unregisterNetworkCallback()
        destroyWebView()
        super.onDestroy()
    }

    private fun applyImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val view = WebView(this)
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        view.setBackgroundColor(Color.parseColor("#070b16"))
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.isLongClickable = false
        view.isHapticFeedbackEnabled = false
        view.setOnLongClickListener { true } // pas de menu de sélection de texte

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            // Indispensable : le son de l'adhan doit partir sans clic ni télécommande.
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            userAgentString = userAgentString + " NidaaTV/" + BuildConfig.VERSION_NAME
        }
        CookieManager.getInstance().setAcceptCookie(true)

        view.addJavascriptInterface(
            NidaaBridge(onReload = { runOnUiThread { loadScreen() } }),
            NidaaBridge.NAME,
        )
        view.webViewClient = ScreenWebViewClient()
        view.webChromeClient = ScreenChromeClient()
        return view
    }

    private fun ensureWebView(): WebView? {
        webView?.let { return it }
        return try {
            createWebView().also {
                webContainer.addView(it)
                webView = it
                it.requestFocus()
            }
        } catch (e: Throwable) {
            // WebView système absent ou en cours de mise à jour : on réessaiera.
            Log.e(TAG, "Impossible de créer le WebView", e)
            null
        }
    }

    private fun destroyWebView() {
        val view = webView ?: return
        webView = null
        try {
            webContainer.removeView(view)
            view.stopLoading()
            view.removeJavascriptInterface(NidaaBridge.NAME)
            view.webChromeClient = null
            view.destroy()
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur à la destruction du WebView", e)
        }
    }

    /** (Re)charge l'URL configurée. Appelée au démarrage, au réessai, depuis les réglages et le pont JS. */
    fun loadScreen() {
        handler.removeCallbacks(retryRunnable)
        val url = Prefs.screenUrl(this)
        fallbackUrl.text = url
        val view = ensureWebView()
        if (view == null) {
            showFallback()
            return
        }
        Log.i(TAG, "Chargement de $url")
        view.loadUrl(url)
    }

    private fun showFallback() {
        pageFailed = true
        handler.removeCallbacks(unlockAudioRunnable)
        webView?.visibility = View.INVISIBLE
        fallback.visibility = View.VISIBLE
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, RETRY_DELAY_MS)
    }

    private fun showWeb() {
        pageFailed = false
        handler.removeCallbacks(retryRunnable)
        fallback.visibility = View.GONE
        webView?.let {
            it.visibility = View.VISIBLE
            if (!settingsDialog.isShowing) it.requestFocus()
        }
        handler.removeCallbacks(unlockAudioRunnable)
        handler.post(unlockAudioRunnable)
    }

    /** « Vider le cache et dissocier » : efface toutes les données web (jeton d'appairage compris). */
    fun clearDataAndReload() {
        try {
            webView?.apply {
                stopLoading()
                clearCache(true)
                clearHistory()
                clearFormData()
            }
            WebStorage.getInstance().deleteAllData() // localStorage, IndexedDB, Cache Storage, service workers
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur pendant l'effacement des données", e)
        }
        // WebView neuf : aucune donnée en mémoire ne survit à l'effacement.
        destroyWebView()
        Toast.makeText(this, R.string.clear_done, Toast.LENGTH_SHORT).show()
        loadScreen()
    }

    fun quit() {
        finishAndRemoveTask()
    }

    fun onSettingsClosed() {
        applyImmersive()
        if (!pageFailed) webView?.requestFocus()
    }

    private fun isSameOrigin(target: Uri): Boolean {
        val base = Uri.parse(Prefs.screenUrl(this))
        fun port(u: Uri) = if (u.port != -1) u.port else if (u.scheme.equals("https", true)) 443 else 80
        return target.scheme.equals(base.scheme, ignoreCase = true) &&
            target.host.equals(base.host, ignoreCase = true) &&
            port(target) == port(base)
    }

    private inner class ScreenWebViewClient : WebViewClient() {

        /** Kiosque : la page principale ne quitte jamais l'origine de l'écran. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            return blockIfForeign(request.url)
        }

        @Deprecated("Variante utilisée avant Android 7")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            blockIfForeign(Uri.parse(url))

        private fun blockIfForeign(target: Uri): Boolean {
            if (isSameOrigin(target)) return false
            Log.w(TAG, "Navigation hors origine bloquée : $target")
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            // La page d'erreur interne de Chromium ne doit pas effacer l'échec en cours.
            if (url == null || !url.startsWith("chrome-error://")) loadError = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (view !== webView) return
            if (loadError) showFallback() else showWeb()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame || view !== webView) return
            Log.w(TAG, "Échec du chargement (${error.errorCode} ${error.description}) : ${request.url}")
            loadError = true
            showFallback()
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!request.isForMainFrame || view !== webView) return
            Log.w(TAG, "Erreur HTTP ${errorResponse.statusCode} : ${request.url}")
            loadError = true
            showFallback()
        }

        /** Processus de rendu tué (mémoire) ou planté : on recrée le WebView au lieu de laisser l'appli mourir. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail.didCrash() else false
            Log.e(TAG, "Processus de rendu perdu (crash=$crashed) : recréation du WebView")
            if (view === webView) {
                destroyWebView()
                handler.post { loadScreen() }
            } else {
                try {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                } catch (e: Throwable) {
                    Log.w(TAG, "Erreur à la destruction d'un ancien WebView", e)
                }
            }
            return true
        }
    }

    private inner class ScreenChromeClient : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            val text = "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
            when (message.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, text)
                ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, text)
                ConsoleMessage.MessageLevel.DEBUG -> Log.d(TAG, text)
                else -> Log.i(TAG, text)
            }
            return true
        }
    }

    // ------------------------------------------------------------------ réseau

    private fun registerNetworkCallback() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Ne recharge QUE si la page avait échoué : une page qui fonctionne n'est jamais rechargée.
                handler.post {
                    if (pageFailed && !isFinishing) {
                        Log.i(TAG, "Réseau de retour : nouvelle tentative")
                        loadScreen()
                    }
                }
            }
        }
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            manager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Suivi du réseau indisponible", e)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Désinscription du suivi réseau impossible", e)
        }
    }

    // ------------------------------------------------------------------ télécommande

    /**
     * Kiosque : BACK seul ne fait rien. Le menu de réglages s'ouvre par
     * MENU, par un appui long (2 s) sur OK, ou par 5 appuis sur BACK en moins de 3 s.
     * (Quand le menu est ouvert, c'est sa propre fenêtre qui reçoit les touches.)
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedOnce()
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP) openSettings()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        okLongPressFired = false
                        handler.removeCallbacks(okLongPressRunnable)
                        handler.postDelayed(okLongPressRunnable, OK_LONG_PRESS_MS)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(okLongPressRunnable)
                    if (okLongPressFired) {
                        okLongPressFired = false
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onBackPressedOnce() {
        val now = SystemClock.uptimeMillis()
        backPresses.addLast(now)
        while (backPresses.isNotEmpty() && now - backPresses.first() > BACK_WINDOW_MS) backPresses.removeFirst()
        if (backPresses.size >= BACK_COUNT) {
            backPresses.clear()
            openSettings()
        }
    }

    private fun openSettings() {
        handler.removeCallbacks(okLongPressRunnable)
        backPresses.clear()
        settingsDialog.show()
    }

    companion object {
        const val TAG = "NidaaTV"
        private const val RETRY_DELAY_MS = 30_000L
        private const val OK_LONG_PRESS_MS = 2_000L
        private const val BACK_COUNT = 5
        private const val BACK_WINDOW_MS = 3_000L
        private const val UNLOCK_AUDIO_PERIOD_MS = 15_000L

        /**
         * L'écran web ne « déverrouille » son AudioContext qu'après un événement pointerdown/keydown
         * (contrainte des navigateurs). Ici la lecture sans geste est autorisée par le WebView :
         * on émet donc un pointerdown synthétique pour que la page active le son d'elle-même.
         * Répété périodiquement car l'écouteur n'est posé qu'une fois l'écran appairé et affiché ;
         * sans effet une fois le son déverrouillé (la page retire alors son écouteur).
         */
        private const val UNLOCK_AUDIO_JS =
            "(function(){try{window.dispatchEvent(new Event('pointerdown'));}catch(e){}})();"
    }
}
