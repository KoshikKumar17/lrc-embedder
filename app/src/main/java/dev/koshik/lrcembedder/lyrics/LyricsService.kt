package dev.koshik.lrcembedder.lyrics

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

object Scoring {
    /** 0..~110. Title matters most, then artist, then duration, small bonus for synced. */
    fun score(q: SongQuery, title: String, artist: String, durationSec: Double?, synced: Boolean): Double {
        val t = FuzzyMatch.score(QueryCleaner.cleanTitle(title), q.title)
        val a = if (q.artist.isBlank() || artist.isBlank()) {
            t
        } else {
            maxOf(
                FuzzyMatch.score(artist, q.artist),
                FuzzyMatch.score(artist, q.primaryArtist.ifBlank { q.artist })
            )
        }
        var s = t * 0.65 + a * 0.35
        if (durationSec != null && q.durationSec != null) {
            val d = abs(durationSec - q.durationSec)
            s += when {
                d <= 3 -> 8.0
                d > 10 -> -30.0
                d > 6 -> -12.0
                else -> 0.0
            }
        }
        if (synced) s += 5.0
        return s
    }

    fun titleScore(q: SongQuery, title: String): Double =
        FuzzyMatch.score(QueryCleaner.cleanTitle(title), q.title)
}

object LyricsService {
    /** Both providers in parallel, best match first. */
    suspend fun searchAll(q: SongQuery): List<LyricsResult> = coroutineScope {
        val lrclib = async { safe { LrcLibClient.search(q) } ?: emptyList() }
        val netease = async { safe { NeteaseClient.search(q) } ?: emptyList() }
        val qq = async { safe { QQMusicClient.search(q) } ?: emptyList() }
        (lrclib.await() + netease.await() + qq.await())
            .map {
                it.copy(
                    score = Scoring.score(q, it.title, it.artist, it.durationSec, it.isSynced),
                    titleScore = Scoring.titleScore(q, it.title)
                )
            }
            .sortedByDescending { it.score }
    }

    /** Automatic pick for batch mode: a confident match, synced preferred. Null = not found. */
    suspend fun findBest(q: SongQuery): LyricsResult? {
        val accepted = searchAll(q).filter { it.score >= 72.0 && it.titleScore >= 60.0 }
        return accepted.firstOrNull { it.isSynced } ?: accepted.firstOrNull()
    }
}
