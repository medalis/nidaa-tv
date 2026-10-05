package dj.nidaa.tv

import android.app.AlertDialog
import android.os.Build
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewCompat

/** Menu de réglages « secret » de l'installateur, entièrement navigable au D-pad. */
class SettingsDialog(private val activity: MainActivity) {

    private var dialog: AlertDialog? = null

    val isShowing: Boolean get() = dialog?.isShowing == true

    fun show() {
        if (isShowing || activity.isFinishing) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null)
        val urlField = view.findViewById<EditText>(R.id.settings_url)
        val saveButton = view.findViewById<Button>(R.id.settings_save_url)
        val resetButton = view.findViewById<Button>(R.id.settings_reset_url)
        val reloadButton = view.findViewById<Button>(R.id.settings_reload)
        val clearButton = view.findViewById<Button>(R.id.settings_clear)
        val autostartSwitch = view.findViewById<Switch>(R.id.settings_autostart)
        val versionText = view.findViewById<TextView>(R.id.settings_version)
        val quitButton = view.findViewById<Button>(R.id.settings_quit)
        val closeButton = view.findViewById<Button>(R.id.settings_close)

        val created = AlertDialog.Builder(activity, R.style.Theme_Nidaa_Dialog)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setCancelable(true) // la touche BACK ferme le menu
            .create()
        dialog = created

        // Adresse non modifiable depuis l'écran (tout se règle dans le back-office) : champ et boutons masqués.
        urlField.setText(Prefs.screenUrl(activity))
        urlField.isEnabled = false
        saveButton.visibility = android.view.View.GONE
        resetButton.visibility = android.view.View.GONE

        fun saveUrl() {
            val url = urlField.text.toString().trim()
            if (!Prefs.isValidUrl(url)) {
                Toast.makeText(activity, R.string.settings_url_invalid, Toast.LENGTH_LONG).show()
                return
            }
            Prefs.setScreenUrl(activity, url)
            Toast.makeText(activity, R.string.settings_url_saved, Toast.LENGTH_SHORT).show()
            created.dismiss()
            activity.loadScreen()
        }

        urlField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveUrl()
                true
            } else {
                false
            }
        }
        saveButton.setOnClickListener { saveUrl() }
        resetButton.setOnClickListener {
            Prefs.resetScreenUrl(activity)
            // Adresse non modifiable depuis l'écran (tout se règle dans le back-office) : champ et boutons masqués.
        urlField.setText(Prefs.screenUrl(activity))
        urlField.isEnabled = false
        saveButton.visibility = android.view.View.GONE
        resetButton.visibility = android.view.View.GONE
        }
        reloadButton.setOnClickListener {
            created.dismiss()
            activity.loadScreen()
        }
        clearButton.setOnClickListener {
            AlertDialog.Builder(activity, R.style.Theme_Nidaa_Dialog)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_message)
                .setNegativeButton(R.string.clear_confirm_no, null)
                .setPositiveButton(R.string.clear_confirm_yes) { _, _ ->
                    created.dismiss()
                    activity.clearDataAndReload()
                }
                .show()
        }

        autostartSwitch.isChecked = Prefs.autostart(activity)
        autostartSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setAutostart(activity, checked) }

        val detectedWebView: String? = try {
            WebViewCompat.getCurrentWebViewPackage(activity)?.versionName
        } catch (e: Exception) {
            null
        }
        val webViewVersion = detectedWebView ?: activity.getString(R.string.settings_webview_unknown)
        versionText.text = activity.getString(
            R.string.settings_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            webViewVersion,
            Build.VERSION.RELEASE ?: "?",
        )

        quitButton.setOnClickListener {
            created.dismiss()
            activity.quit()
        }
        closeButton.setOnClickListener { created.dismiss() }

        created.setOnDismissListener {
            dialog = null
            activity.onSettingsClosed()
        }
        created.show()
        // Focus initial sur « Recharger » : évite d'ouvrir le clavier virtuel d'emblée.
        reloadButton.requestFocus()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
