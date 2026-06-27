package de.jeanlucmakiola.calendula.data.ics

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android IO edge of `.ics` export: writes a serialised calendar to a
 * SAF document (whole-calendar backup) or stages it in a cache file behind a
 * `FileProvider` content Uri (single-event share). The serialisation itself is
 * the pure `domain.ics.IcsWriter`; this class only moves bytes.
 */
@Singleton
class IcsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Write [content] to the SAF document at [uri] as UTF-8. Throws on failure. */
    fun writeDocument(uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            // Only the scheme — the full Uri can embed the user's chosen filename.
        } ?: throw IOException("Could not open output stream for export (scheme=${uri.scheme})")
    }

    /**
     * Write [content] to [fileName] inside the persisted SAF tree [folder],
     * overwriting it if it already exists (the automatic-backup destination).
     * Requires a persisted write grant on [folder]. Throws on failure.
     */
    fun writeToFolder(folder: Uri, fileName: String, content: String) {
        val dir = DocumentFile.fromTreeUri(context, folder)
            ?: throw IOException("Backup folder is not accessible")
        if (!dir.exists() || !dir.canWrite()) {
            throw IOException("Backup folder is missing or not writable")
        }
        // Reuse the canonical file if present and clean up any "name (1).ics"
        // duplicates SAF may have created if two runs ever raced — so we always
        // converge on a single overwritten file.
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var target: DocumentFile? = null
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            when {
                name == fileName -> target = child
                name.startsWith("$base (") && name.endsWith(".$ext") -> child.delete()
            }
        }
        val file = target
            ?: dir.createFile(MIME_CALENDAR, fileName)
            ?: throw IOException("Could not create backup file in the chosen folder")
        writeDocument(file.uri, content)
    }

    /**
     * Stage [content] in a private cache file and return a shareable content
     * Uri for an `ACTION_SEND`. [fileName] is the suggested `.ics` name shown to
     * the receiving app. The authority mirrors the manifest's `FileProvider`.
     */
    fun stageShareFile(fileName: String, content: String): Uri {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        const val SHARE_DIR = "shared_ics"
        const val MIME_CALENDAR = "text/calendar"
    }
}
