package dev.koshik.lrcembedder.data

import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import java.io.File

data class EmbedOutcome(
    val embedded: Boolean,
    val lrcFileSaved: Boolean,
    val error: String?
)

/**
 * Writes lyrics into the file's own tags with TagLib (ID3 USLT for MP3, ©lyr for M4A/MP4,
 * LYRICS comment for FLAC/OGG/Opus, ...). Formats that can't hold tags fall back to a
 * sidecar .lrc file next to the song.
 */
object LyricsEmbedder {
    fun embed(path: String, lyrics: String, alsoLrc: Boolean): EmbedOutcome {
        var embedded = false
        var error: String? = null
        try {
            embedded = writeTag(path, lyrics)
            if (!embedded) error = "Verification failed"
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        var lrc = false
        if (alsoLrc || !embedded) {
            lrc = writeSidecar(path, lyrics)
        }
        return EmbedOutcome(embedded, lrc, error)
    }

    private fun writeTag(path: String, lyrics: String): Boolean {
        val file = File(path)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        try {
            val metadata = TagLib.getMetadata(pfd.dup().detachFd(), false) ?: return false
            TagLib.savePropertyMap(
                pfd.dup().detachFd(),
                propertyMap = metadata.propertyMap.apply { put("LYRICS", arrayOf(lyrics)) }
            )
        } finally {
            pfd.close()
        }
        return readBack(file)
    }

    /** Re-opens the file and checks the lyrics really are in there. */
    private fun readBack(file: File): Boolean {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            val md = TagLib.getMetadata(pfd.dup().detachFd(), false)
            md?.propertyMap?.get("LYRICS")?.firstOrNull()?.isNotBlank() == true
        } finally {
            pfd.close()
        }
    }

    private fun writeSidecar(path: String, lyrics: String): Boolean = try {
        File(path.substringBeforeLast('.') + ".lrc").writeText(lyrics)
        true
    } catch (e: Exception) {
        false
    }
}
