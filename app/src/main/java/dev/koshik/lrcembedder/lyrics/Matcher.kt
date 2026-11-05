package dev.koshik.lrcembedder.lyrics

import dev.koshik.lrcembedder.lyrics.matching.ConfidenceBreakdown
import dev.koshik.lrcembedder.lyrics.matching.ConfidenceScorer
import dev.koshik.lrcembedder.lyrics.matching.FilenameParser
import dev.koshik.lrcembedder.lyrics.matching.LocalTrack
import dev.koshik.lrcembedder.lyrics.matching.MatchStrategy
import dev.koshik.lrcembedder.lyrics.matching.MatchTier
import dev.koshik.lrcembedder.lyrics.matching.ProviderResult
import dev.koshik.lrcembedder.lyrics.matching.QueryCandidate
import dev.koshik.lrcembedder.lyrics.matching.TextMatch

/**
 * Query-ladder + scoring glue, ported from MusicResync's SmartLyricsMatcher:
 * parse tags/file name several ways, score every hit against every reading, veto wrong singers.
 */
internal object Matcher {
    private val artistPartSplit = Regex(
        """\s*(?:,|&|;|/|\+|\bfeat\.?\s|\bft\.?\s|\bfeaturing\b|\bx\b)\s*""",
        RegexOption.IGNORE_CASE
    )

    /** Individual names of a joined artist string; empty when it is a single name. */
    fun artistParts(artist: String): List<String> {
        val parts = artist.split(artistPartSplit).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size > 1) parts else emptyList()
    }

    fun localTrack(q: SongQuery): LocalTrack = LocalTrack(
        title = q.title.takeIf { it.isNotBlank() },
        artist = q.artist.takeUnless { TextMatch.isJunkArtist(it) },
        durationSec = q.durationSec
    )

    /** Ordered best-guess (title, artist) readings of the song. */
    fun candidates(q: SongQuery): List<QueryCandidate> {
        val out = LinkedHashMap<String, QueryCandidate>()
        fun add(c: QueryCandidate) {
            if (c.title.isBlank()) return
            val key = TextMatch.normalizeForCompare(c.title) + "|" + TextMatch.normalizeForCompare(c.artist)
            out.putIfAbsent(key, c)
        }

        val artist = q.artist.takeUnless { TextMatch.isJunkArtist(it) }
        add(QueryCandidate(q.title, artist, MatchStrategy.TAGS))
        if (q.primaryArtist.isNotBlank() && !q.primaryArtist.equals(artist, ignoreCase = true)) {
            add(QueryCandidate(q.title, q.primaryArtist, MatchStrategy.FILENAME_PRIMARY_ARTIST))
        }
        val parsed = FilenameParser.candidates(
            q.rawTitle.ifBlank { q.title },
            q.rawArtist.ifBlank { q.artist },
            q.path
        )
        for (c in parsed) {
            val t = QueryCleaner.cleanTitle(c.title).ifBlank { c.title }
            add(c.copy(title = t))
        }
        return out.values.take(8)
    }

    fun artistGuesses(local: LocalTrack, cands: List<QueryCandidate>): List<String> =
        (listOfNotNull(local.artist) + cands.mapNotNull { it.artist })
            .filter { it.isNotBlank() }
            .distinct()

    /** Best confidence over every reading of the local song, and over each name of a joined artist. */
    fun score(local: LocalTrack, cand: QueryCandidate, raw: RawHit): ConfidenceBreakdown {
        val title = QueryCleaner.cleanTitle(raw.title).ifBlank { raw.title }
        val variants = listOf(raw.artist) + artistParts(raw.artist)
        return variants
            .map { a ->
                scoreHitAgainstViews(local, ProviderResult(title, a, raw.durationSec, raw.album, true), cand)
            }
            .maxByOrNull { it.score }!!
    }

    /** Remix/loosened fallbacks never auto-accept; artist-less readings only count with exact duration. */
    private fun scoreHitAgainstViews(local: LocalTrack, pr: ProviderResult, cand: QueryCandidate): ConfidenceBreakdown {
        var best = ConfidenceScorer.score(local, pr)
        val view = LocalTrack(cand.title, cand.artist, local.durationSec, local.album)
        if (view.title != local.title || view.artist != local.artist) {
            val viewConf = ConfidenceScorer.score(view, pr)
            val viewTrustable = !cand.artist.isNullOrBlank() || viewConf.durationMatched
            if (viewTrustable && viewConf.score > best.score) best = viewConf
        }
        return if (cand.strategy == MatchStrategy.FILENAME_LOOSE && best.tier == MatchTier.AUTO_ACCEPT)
            best.copy(score = 0.80, tier = MatchTier.REVIEW)
        else best
    }

    /** True when the provider names an artist that agrees with none of our guesses (and length isn't exact). */
    fun vetoed(guesses: List<String>, resultArtist: String?, conf: ConfidenceBreakdown): Boolean {
        if (conf.durationMatched) return false
        return disagrees(guesses, resultArtist)
    }

    fun disagrees(guesses: List<String>, resultArtist: String?): Boolean {
        if (resultArtist.isNullOrBlank()) return false
        val g = guesses.filter { TextMatch.normalizeForCompare(it).isNotEmpty() }
        if (g.isEmpty()) return false
        val variants = listOf(resultArtist) + artistParts(resultArtist)
        return variants.all { v ->
            if (TextMatch.normalizeForCompare(v).isEmpty()) true
            else g.maxOf { TextMatch.similarity(it, v) } < 0.50
        }
    }
}
