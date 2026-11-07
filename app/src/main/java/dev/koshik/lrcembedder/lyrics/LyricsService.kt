package dev.koshik.lrcembedder.lyrics

import dev.koshik.lrcembedder.lyrics.matching.ConfidenceBreakdown
import dev.koshik.lrcembedder.lyrics.matching.ConfidenceScorer
import dev.koshik.lrcembedder.lyrics.matching.LocalTrack
import dev.koshik.lrcembedder.lyrics.matching.MatchTier
import dev.koshik.lrcembedder.lyrics.matching.ProviderResult
import dev.koshik.lrcembedder.lyrics.matching.QueryCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lyrics search using MusicResync's matching: several readings of the song (tags, "Artist - Title" file
 * names, primary artist, loosened titles) x several providers in parallel, every hit confidence-scored
 * (title/artist/duration), wrong singers vetoed, and an iTunes/Deezer "last resort" for garbled names.
 * Several optimizations by Koshik Kumar - https://github.com/KoshikKumar17
 */
object LyricsService {
    private const val PROVIDER_TIMEOUT_MS = 25_000L
    private const val REQUEST_DELAY_MS = 150L
    private const val MAX_QUERIES = 6
    /** Spotify is a metered API (free tier ~1000 requests/month), so it gets very few queries per song. */
    private const val SPOTIFY_MAX_QUERIES = 2
    private const val MAX_LYRIC_FETCH = 4
    private const val MIN_LISTED = 0.45
    private const val DEEZER_MIN_TITLE = 0.55

    private class Scored(val raw: RawHit, val conf: ConfidenceBreakdown)

    /** A result good enough that paying for a Spotify request is pointless. */
    private fun believable(r: LyricsResult) =
        !r.rejected && r.score >= ConfidenceScorer.REVIEW_THRESHOLD * 100

    /**
     * Free providers in parallel first; trustworthy matches first, doubtful ones after (still selectable).
     * Spotify (metered RapidAPI) is only queried as a last resort, when nothing believable was found.
     */
    suspend fun searchAll(q: SongQuery): List<LyricsResult> = coroutineScope {
        val local = Matcher.localTrack(q)
        val cands = Matcher.candidates(q)
        val guesses = Matcher.artistGuesses(local, cands)

        val jobs = Provider.values().filter { it != Provider.SPOTIFY }.map { p ->
            async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { searchProvider(p, local, cands, guesses) }
                    ?: emptyList()
            }
        }
        val results = jobs.awaitAll().flatten().toMutableList()
        if (results.none { !it.rejected }) {
            results += safe { rescue(local, cands, guesses) } ?: emptyList()
        }
        if (SpotifyClient.enabled && results.none { believable(it) }) {
            results += withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                searchProvider(Provider.SPOTIFY, local, cands, guesses, SPOTIFY_MAX_QUERIES)
            } ?: emptyList()
        }
        results.sortedWith(compareBy<LyricsResult> { it.rejected }.thenByDescending { it.score })
    }

    /** Automatic pick for batch mode: a believable match, synced preferred. Null = not found. */
    suspend fun findBest(q: SongQuery): LyricsResult? {
        val ok = searchAll(q).filter { !it.rejected && it.score >= ConfidenceScorer.REVIEW_THRESHOLD * 100 }
        val top = ok.firstOrNull() ?: return null
        if (top.isSynced) return top
        return ok.firstOrNull { it.isSynced && it.score >= top.score - 10.0 } ?: top
    }

    private suspend fun searchProvider(
        p: Provider,
        local: LocalTrack,
        cands: List<QueryCandidate>,
        guesses: List<String>,
        maxQueries: Int = MAX_QUERIES
    ): List<LyricsResult> {
        val scored = LinkedHashMap<String, Scored>()
        var best = 0.0
        for (cand in cands.take(maxQueries)) {
            val raws = safe { withRetry { query(p, cand) } } ?: emptyList()
            for (raw in raws) {
                var conf = Matcher.score(local, cand, raw)
                if (Matcher.vetoed(guesses, raw.artist, conf)) conf = conf.copy(tier = MatchTier.REJECT)
                if (p == Provider.DEEZER &&
                    (conf.tier == MatchTier.REJECT || conf.score < ConfidenceScorer.REVIEW_THRESHOLD ||
                        conf.title < DEEZER_MIN_TITLE)
                ) continue
                val key = "${raw.title}|${raw.artist}|${raw.durationSec}"
                val old = scored[key]
                if (old == null || conf.score > old.conf.score) scored[key] = Scored(raw, conf)
                if (conf.tier != MatchTier.REJECT && conf.score > best) best = conf.score
            }
            if (best >= ConfidenceScorer.AUTO_ACCEPT_THRESHOLD) break
            delay(REQUEST_DELAY_MS)
        }

        val top = scored.values
            .filter { it.conf.score >= MIN_LISTED }
            .sortedWith(compareBy<Scored> { it.conf.tier == MatchTier.REJECT }.thenByDescending { it.conf.score })
            .take(MAX_LYRIC_FETCH)
        return coroutineScope {
            top.map { s -> async { toResult(s) } }.awaitAll().filterNotNull()
        }
    }

    private suspend fun query(p: Provider, cand: QueryCandidate): List<RawHit> {
        val text = cand.asSearchString()
        if (text.isBlank()) return emptyList()
        return when (p) {
            Provider.LRCLIB -> {
                val free = LrcLibClient.search(text)
                val structured = if (!cand.artist.isNullOrBlank()) {
                    safe { LrcLibClient.searchStructured(cand.title, cand.artist) } ?: emptyList()
                } else emptyList()
                free + structured
            }
            Provider.NETEASE -> NeteaseClient.search(text)
            Provider.QQ -> QQMusicClient.search(text)
            Provider.DEEZER -> DeezerClient.search(text)
            Provider.SPOTIFY -> SpotifyClient.search(text)
        }
    }

    private suspend fun toResult(s: Scored): LyricsResult? {
        val raw = s.raw
        var synced = raw.synced
        var plain = raw.plain
        if (synced == null && plain == null) {
            val fetch = raw.fetch ?: return null
            val body = safe { withRetry { fetch() } } ?: return null
            synced = body.synced
            plain = body.plain
        }
        if (synced == null && plain == null) return null
        return LyricsResult(
            provider = raw.provider,
            title = raw.title,
            artist = raw.artist,
            album = raw.album,
            durationSec = raw.durationSec,
            synced = synced,
            plain = plain,
            score = s.conf.score * 100,
            titleScore = s.conf.title * 100,
            rejected = s.conf.tier == MatchTier.REJECT
        )
    }

    /**
     * Only when nothing believable was found: canonicalize the messy name via iTunes/Deezer, re-query LRCLib
     * under the clean name, and accept only hits that still agree with the file (title, length, singer).
     */
    private suspend fun rescue(
        local: LocalTrack,
        cands: List<QueryCandidate>,
        guesses: List<String>
    ): List<LyricsResult> {
        val queries = cands.map { it.asSearchString() }.filter { it.isNotBlank() }.distinct().take(3)
        if (queries.isEmpty()) return emptyList()

        val raws: List<RawHit> = coroutineScope {
            queries.map { qy ->
                async {
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        val out = ArrayList<RawHit>()
                        for (m in RescueClient.canonicalize(qy)) {
                            out += safe { LrcLibClient.search("${m.artist} ${m.title}") } ?: emptyList()
                            delay(REQUEST_DELAY_MS)
                        }
                        out
                    } ?: emptyList<RawHit>()
                }
            }.awaitAll().flatten()
        }

        val views = buildList {
            add(local)
            cands.forEach { add(LocalTrack(it.title, it.artist, local.durationSec, null)) }
        }.distinct()
        val localHasDuration = (local.durationSec ?: 0.0) > 0

        val seen = HashSet<String>()
        val out = ArrayList<LyricsResult>()
        for (raw in raws) {
            if (raw.synced == null && raw.plain == null) continue
            if (!seen.add("${raw.title}|${raw.artist}|${raw.durationSec}")) continue
            val pr = ProviderResult(
                QueryCleaner.cleanTitle(raw.title).ifBlank { raw.title },
                raw.artist, raw.durationSec, raw.album, true
            )
            val conf = views.map { ConfidenceScorer.score(it, pr) }.maxByOrNull { it.score } ?: continue
            if (conf.tier == MatchTier.REJECT) continue
            if (localHasDuration && conf.duration <= 0.0) continue
            if (!conf.durationMatched && conf.title < 0.55) continue
            if (Matcher.vetoed(guesses, raw.artist, conf)) continue
            out += LyricsResult(
                provider = raw.provider,
                title = raw.title,
                artist = raw.artist,
                album = raw.album,
                durationSec = raw.durationSec,
                synced = raw.synced,
                plain = raw.plain,
                score = minOf(conf.score, 0.84) * 100,
                titleScore = conf.title * 100
            )
        }
        return out
    }
}
