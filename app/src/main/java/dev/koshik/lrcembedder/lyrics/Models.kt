package dev.koshik.lrcembedder.lyrics

enum class Provider(val label: String) {
    LRCLIB("LRCLib"),
    NETEASE("Netease"),
    QQ("QQ Music"),
    DEEZER("Deezer")
}

data class LyricsResult(
    val provider: Provider,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Double?,
    val synced: String?,
    val plain: String?,
    val score: Double = 0.0,
    val titleScore: Double = 0.0,
    /** Matcher thinks this is probably a different song/singer. Still listed for manual choice. */
    val rejected: Boolean = false
) {
    /** Synced LRC when available, otherwise plain text. */
    val best: String? get() = synced ?: plain
    val isSynced: Boolean get() = synced != null
    val key: String get() = "${provider.name}|$title|$artist|${durationSec ?: 0.0}"
}

data class SongQuery(
    val title: String,
    val artist: String,
    val primaryArtist: String,
    val durationSec: Double?,
    val album: String? = null,
    /** Audio file path, used to recover "Artist - Title" from messy file names. */
    val path: String? = null,
    /** Untouched tag values, so the file-name/tag parser sees what is really on disk. */
    val rawTitle: String = "",
    val rawArtist: String = ""
)

/** Lyrics body resolved from a search hit (some providers need a second request). */
class LyricsBody(val synced: String?, val plain: String?)

/**
 * One search hit from a provider, before scoring. [fetch] resolves the lyrics lazily for providers
 * (Netease, QQ, Deezer) where search results don't carry them.
 */
class RawHit(
    val provider: Provider,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Double?,
    val synced: String? = null,
    val plain: String? = null,
    val fetch: (suspend () -> LyricsBody?)? = null
)
