package de.jeanlucmakiola.calendula.data.ics

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android IO edge of `.ics` import: reads the text of a received/opened
 * document [Uri]. Parsing is the pure `domain.ics.IcsParser`; this class only
 * pulls bytes off the ContentResolver. Returns null on any read failure.
 */
@Singleton
class IcsImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()
}
