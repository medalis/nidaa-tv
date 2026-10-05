package dj.nidaa.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Relance l'écran au démarrage de la box (et après une mise à jour de l'appli),
 * si l'option « Démarrer au boot » est activée.
 *
 * Remarque : à partir d'Android 10, le système peut refuser le lancement d'une activité
 * depuis l'arrière-plan. La méthode fiable reste de définir Nidaa comme lanceur (HOME).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ACTIONS) return
        if (!Prefs.autostart(context)) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (e: Exception) {
            Log.w(MainActivity.TAG, "Démarrage automatique refusé par le système", e)
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
