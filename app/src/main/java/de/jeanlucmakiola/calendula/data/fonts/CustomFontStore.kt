package de.jeanlucmakiola.calendula.data.fonts

import android.content.Context
import android.net.Uri
import de.jeanlucmakiola.calendula.domain.FontRole
import java.io.File
import java.util.Locale
import android.graphics.fonts.Font as PlatformFont

/**
 * Storage for user-loaded custom fonts (issue #19). A font a user picks via the
 * system file picker is copied into the app's private storage — one file per
 * [FontRole] — so the selection survives even if the original is later moved or
 * deleted, and never leaves the app. The chosen file is validated as a real font
 * before it replaces the previous one, so a bad pick can't wedge the app-wide
 * typography.
 */
object CustomFontStore {

    private fun dir(context: Context): File = File(context.filesDir, "fonts")

    /** The stored font file for [role] (may not exist yet). */
    fun file(context: Context, role: FontRole): File =
        File(dir(context), "${role.name.lowercase(Locale.ROOT)}.ttf")

    fun exists(context: Context, role: FontRole): Boolean =
        file(context, role).let { it.exists() && it.length() > 0 }

    /**
     * Copy [uri] into per-role storage, first validating that it parses as a
     * font. Returns true on success; on any failure the previous file is left
     * untouched and false is returned (the caller keeps the old selection).
     */
    fun import(context: Context, role: FontRole, uri: Uri): Boolean {
        val target = file(context, role)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${role.name.lowercase(Locale.ROOT)}.tmp")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            // Font.Builder throws IOException for anything that isn't a valid
            // font file (API 29+, which is our minSdk) — a cheap, reliable check.
            PlatformFont.Builder(tmp).build()
            if (target.exists() && !target.delete()) return false
            tmp.renameTo(target)
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    /** Forget the custom font for [role] (used when switching away from it). */
    fun clear(context: Context, role: FontRole) {
        file(context, role).delete()
    }
}
