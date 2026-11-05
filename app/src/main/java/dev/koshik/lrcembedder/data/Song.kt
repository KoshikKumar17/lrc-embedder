package dev.koshik.lrcembedder.data

import android.content.ContentUris
import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val durationMs: Long,
    val albumId: Long,
    val dateAdded: Long
) {
    /** File type shown as a badge, e.g. MP3, M4A, FLAC. */
    val extension: String get() = path.substringAfterLast('.', "").uppercase()

    val artUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}
