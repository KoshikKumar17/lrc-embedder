package dev.koshik.lrcembedder.data

import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import java.io.File

object SongRepository {
    private val skipPathParts = listOf("/voice notes/", "/recordings/", "/sound_recorder/", "/call/")

    /** Every audio file MediaStore knows about (mp3, m4a, aac, flac, ogg, opus, wav ...). */
    fun load(context: Context): List<Song> {
        val projection = arrayOf(
            Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM, Media.ALBUM_ID, Media.DATA, Media.DURATION, Media.DATE_ADDED
        )
        val selection =
            "${Media.IS_RINGTONE} = 0 AND ${Media.IS_ALARM} = 0 AND ${Media.IS_NOTIFICATION} = 0 AND ${Media.DURATION} >= 15000"

        val out = ArrayList<Song>()
        context.contentResolver.query(
            Media.EXTERNAL_CONTENT_URI, projection, selection, null, "${Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Media._ID)
            val iTitle = c.getColumnIndexOrThrow(Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(Media.ALBUM_ID)
            val iData = c.getColumnIndexOrThrow(Media.DATA)
            val iDur = c.getColumnIndexOrThrow(Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(Media.DATE_ADDED)
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                val lower = path.lowercase()
                if (skipPathParts.any { it in lower }) continue
                val rawTitle = c.getString(iTitle)
                val title = if (rawTitle.isNullOrBlank() || rawTitle == "<unknown>") {
                    File(path).nameWithoutExtension
                } else {
                    rawTitle
                }
                val rawArtist = c.getString(iArtist).orEmpty()
                out += Song(
                    id = c.getLong(iId),
                    title = title,
                    artist = if (rawArtist == "<unknown>") "" else rawArtist,
                    album = c.getString(iAlbum).orEmpty(),
                    path = path,
                    durationMs = c.getLong(iDur),
                    albumId = c.getLong(iAlbumId),
                    dateAdded = c.getLong(iAdded)
                )
            }
        }
        return out
    }
}
