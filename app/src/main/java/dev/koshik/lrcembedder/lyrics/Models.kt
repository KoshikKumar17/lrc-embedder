package dev.koshik.lrcembedder.lyrics

enum class Provider(val label: String) {
    LRCLIB("LRCLib"),
    NETEASE("Netease"),
    QQ("QQ Music")
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
    val titleScore: Double = 0.0
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
    val durationSec: Double?
)
