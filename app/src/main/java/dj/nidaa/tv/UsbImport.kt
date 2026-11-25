package dj.nidaa.tv

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Import hors ligne par clé USB : lecture du fichier « .nidaa » exporté depuis le back-office
 * (Écrans › Préparer la clé USB). Le contenu (JSON) est transmis tel quel à la page web de l'écran.
 */
object UsbImport {
    private const val MAX_BYTES = 40L * 1024 * 1024

    /** Cherche le fichier .nidaa le plus récent à la racine de la clé (ou dans un dossier « Nidaa »). */
    fun findOnVolume(root: File): File? {
        val dirs = mutableListOf(root)
        root.listFiles()?.filter { it.isDirectory && it.name.equals("nidaa", ignoreCase = true) }?.let { dirs.addAll(it) }
        return dirs.flatMap { d -> d.listFiles()?.toList() ?: emptyList() }
            .filter { it.isFile && it.name.lowercase().endsWith(".nidaa") && it.length() in 1..MAX_BYTES }
            .maxByOrNull { it.lastModified() }
    }

    fun readFile(file: File): String = file.readText(Charsets.UTF_8)

    fun readUri(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Fichier illisible")
        return input.use { s ->
            val bytes = s.readBytes()
            if (bytes.size > MAX_BYTES) throw IllegalStateException("Fichier trop gros")
            String(bytes, Charsets.UTF_8)
        }
    }

    /** Vérifie rapidement qu'il s'agit bien d'un fichier d'écran Nidaa. */
    fun looksValid(json: String): Boolean = json.trimStart().startsWith("{") && json.contains("\"nidaa-screen\"")
}
