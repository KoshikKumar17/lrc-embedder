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
    private const val UA = "LRCEmbedder/1.0 (https://github.com/KoshikKumar17/lrc-embedder)"

    /** Free-text search ("title artist"). */
    suspend fun search(query: String): List<RawHit> {
        if (query.isBlank()) return emptyList()
        return fetch(listOf("q" to query.trim()))
    }

    /** Structured search; far more precise when both fields are known. */
    suspend fun searchStructured(title: String, artist: String): List<RawHit> {
        if (title.isBlank()) return emptyList()
        val params = buildList {
            add("track_name" to title.trim())
            if (artist.isNotBlank()) add("artist_name" to artist.trim())
        }
        return fetch(params)
    }

    private suspend fun fetch(params: List<Pair<String, String>>): List<RawHit> {
        val resp = Http.client.get(URL) {
            params.forEach { (k, v) -> parameter(k, v) }
            header(HttpHeaders.UserAgent, UA)
        }
        if (resp.status.value !in 200..299) return emptyList()
        val arr = Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonArray
            ?: return emptyList()
        val out = ArrayList<RawHit>()
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            (o["id"] as? JsonPrimitive)?.longOrNull ?: continue
            val synced = (o["syncedLyrics"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val plain = (o["plainLyrics"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            if (synced == null && plain == null) continue
            out += RawHit(
                provider = Provider.LRCLIB,
                title = (o["trackName"] as? JsonPrimitive)?.contentOrNull ?: "",
                artist = (o["artistName"] as? JsonPrimitive)?.contentOrNull ?: "",
                album = (o["albumName"] as? JsonPrimitive)?.contentOrNull,
                durationSec = (o["duration"] as? JsonPrimitive)?.doubleOrNull,
                synced = synced,
                plain = plain
            )
        }
        return out
    }
}
