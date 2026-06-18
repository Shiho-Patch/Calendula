package de.jeanlucmakiola.calendula.data.ics

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
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
        } ?: throw IOException("Could not open $uri for writing")
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
    }
}
