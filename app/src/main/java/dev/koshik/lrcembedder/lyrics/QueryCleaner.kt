package dev.koshik.lrcembedder.lyrics

import dev.koshik.lrcembedder.data.Song

/**
 * Turns messy tag/file names into clean search terms.
 *
 *  "Kesariya (From \"Brahmastra\")"          -> "Kesariya"
 *  "Tum Hi Ho - From \"Aashiqui 2\""         -> "Tum Hi Ho"
 *  "Apna Bana Le [Official Video] 320kbps"   -> "Apna Bana Le"
 *  "02 - Channa Mereya (feat. X) - Songspk.com" -> "Channa Mereya"
 */
object QueryCleaner {
    private const val JUNK =
        "from|movie|film|ost|soundtrack|original motion picture|original score|official|" +
            "lyric|lyrics|lyrical|video|audio|hd|hq|4k|remaster|remastered|full song|title track"

    private val ci = RegexOption.IGNORE_CASE

    // (...) [...] {...} that contain a junk word
    private val bracketJunk = Regex("""\s*[(\[{][^)\]}]*\b($JUNK)\b[^)\]}]*[)\]}]""", ci)

    // "(From ...." with no closing bracket
    private val openFrom = Regex("""\s*[(\[{]\s*from\b.*$""", ci)

    // " - From ..." / " | OST ..."
    private val dashFrom = Regex("""\s*[-–—|]\s*(from|ost|original motion picture|original score)\b.*$""", ci)

    // ` from "Movie"`
    private val inlineFromQuoted = Regex("""\s+from\s+["“”'‘’].*$""", ci)

    private val featBracket = Regex("""\s*[(\[]\s*(feat|ft|featuring)\b[^)\]]*[)\]]""", ci)
    private val featInline = Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", ci)

    private val domain = Regex("""\b(www\.)?[a-z0-9-]+\.(com|net|org|in|info|pk|me|co|cc|fm|mobi|biz)\b""", ci)
    private val bitrate = Regex("""\b\d{2,3}\s*kbps\b""", ci)
    private val trackNo = Regex("""^\s*\d{1,3}\s*[-._)]\s*""")
    private val spaces = Regex("""\s+""")
    private val artistSplit = Regex("""\s*(,|&|;|/|\+|\bfeat\.?\s|\bft\.?\s|\bfeaturing\b|\bx\b|\band\b)\s*""", ci)

    fun cleanTitle(raw: String): String {
        var s = raw.replace('_', ' ')
        s = domain.replace(s, " ")
        s = bitrate.replace(s, " ")
        s = bracketJunk.replace(s, "")
        s = openFrom.replace(s, "")
        s = dashFrom.replace(s, "")
        s = inlineFromQuoted.replace(s, "")
        s = featBracket.replace(s, "")
        s = featInline.replace(s, "")
        s = trackNo.replace(s, "")
        s = spaces.replace(s, " ").trim()
        return s.trim('-', '–', '—', '|', ',', '.', ' ')
    }

    fun cleanArtist(raw: String): String {
        if (isUnknown(raw)) return ""
        val s = domain.replace(raw.replace('_', ' '), " ")
        return spaces.replace(s, " ").trim().trim('-', '–', '—', '|', ',', ' ')
    }

    /** Individual artist names, first one is the main artist. */
    fun artists(raw: String): List<String> {
        val cleaned = cleanArtist(raw)
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(artistSplit).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun isUnknown(s: String): Boolean {
        val t = s.trim().lowercase()
        return t.isEmpty() || t == "<unknown>" || t == "unknown" || t == "unknown artist" ||
            t == "various artists" || t == "various"
    }

    fun fromSong(song: Song): SongQuery {
        var title = cleanTitle(song.title)
        var artistRaw = song.artist
        // "Artist - Title" style file names with no artist tag
        if (isUnknown(artistRaw) && " - " in title) {
            val left = title.substringBefore(" - ").trim()
            val right = title.substringAfter(" - ").trim()
            if (left.isNotEmpty() && right.isNotEmpty()) {
                artistRaw = left
                title = right
            }
        }
        return manual(title, artistRaw, song.durationMs / 1000.0).copy(
            path = song.path,
            rawTitle = song.title,
            rawArtist = song.artist
        )
    }

    /** Used for text typed by the user in the search sheet. */
    fun manual(title: String, artist: String, durationSec: Double?): SongQuery {
        val t = cleanTitle(title)
        val a = cleanArtist(artist)
        val primary = artists(artist).firstOrNull().orEmpty()
        return SongQuery(t, a, primary, durationSec?.takeIf { it > 0 }, rawTitle = title, rawArtist = artist)
    }
}
