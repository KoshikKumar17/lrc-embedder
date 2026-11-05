package dev.koshik.lrcembedder.lyrics

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** https://lrclib.net/docs — search results already include the lyrics. */
object LrcLibClient {
    private const val URL = "https://lrclib.net/api/search"
    private const val UA = "LRCEmbedder/1.0 (https://github.com)"

    suspend fun search(q: SongQuery): List<LyricsResult> {
        val attempts = buildList<List<Pair<String, String>>> {
            if (q.artist.isNotBlank()) {
                add(listOf("track_name" to q.title, "artist_name" to q.artist))
            }
            add(listOf("q" to "${q.title} ${q.artist}".trim()))
            if (q.primaryArtist.isNotBlank() && q.primaryArtist != q.artist) {
                add(listOf("track_name" to q.title, "artist_name" to q.primaryArtist))
            }
            add(listOf("track_name" to q.title))
        }

        val seen = HashSet<Long>()
        val out = ArrayList<LyricsResult>()
        for (params in attempts) {
            val arr = safe { fetch(params) } ?: continue
            for (el in arr) {
                val o = el as? JsonObject ?: continue
                val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: continue
                if (!seen.add(id)) continue
                val synced = (o["syncedLyrics"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val plain = (o["plainLyrics"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                if (synced == null && plain == null) continue
                out += LyricsResult(
                    provider = Provider.LRCLIB,
                    title = (o["trackName"] as? JsonPrimitive)?.contentOrNull ?: "",
                    artist = (o["artistName"] as? JsonPrimitive)?.contentOrNull ?: "",
                    album = (o["albumName"] as? JsonPrimitive)?.contentOrNull,
                    durationSec = (o["duration"] as? JsonPrimitive)?.doubleOrNull,
                    synced = synced,
                    plain = plain
                )
            }
            if (out.count { it.synced != null } >= 3) break
        }
        return out
    }

    private suspend fun fetch(params: List<Pair<String, String>>): JsonArray? {
        val resp = Http.client.get(URL) {
            params.forEach { (k, v) -> parameter(k, v) }
            header(HttpHeaders.UserAgent, UA)
        }
        if (resp.status.value !in 200..299) return null
        return Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonArray
    }
}
