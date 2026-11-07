package dev.koshik.lrcembedder.lyrics

import dev.koshik.lrcembedder.BuildConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Spotify lyrics through the RapidAPI "spotify81" `search_lyrics` endpoint.
 * One request returns the best track match together with its lyrics, so no second fetch is needed.
 * Needs a RapidAPI key (Settings); without one the provider is skipped.
 */
object SpotifyClient {
    private const val HOST = "spotify81.p.rapidapi.com"
    private const val URL = "https://$HOST/search_lyrics"

    const val DOCS_URL = "https://rapidapi.com/airaudoeduardo/api/spotify81"

    /** Key compiled into the app (local.properties / CI secret). Blank when the repo has none. */
    val builtInKey: String get() = BuildConfig.RAPIDAPI_KEY.trim()
    val hasBuiltInKey: Boolean get() = builtInKey.isNotBlank()

    /** Key the user typed in Settings (set from Prefs by the ViewModel). Wins over the built-in one. */
    @Volatile
    var userKey: String = ""

    private val activeKey: String get() = userKey.trim().ifBlank { builtInKey }

    /** No key anywhere = provider disabled and other providers carry on. */
    val enabled: Boolean get() = activeKey.isNotBlank()

    suspend fun search(term: String): List<RawHit> {
        if (!enabled || term.isBlank()) return emptyList()
        val resp = Http.client.get(URL) {
            parameter("q", term)
            parameter("format", "json")
            header("Accept", "application/json")
            header("x-rapidapi-host", HOST)
            header("x-rapidapi-key", activeKey)
        }
        if (resp.status.value !in 200..299) return emptyList()
        val root = Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonObject
            ?: return emptyList()
        if ((root["error"] as? JsonPrimitive)?.booleanOrNull == true) return emptyList()

        val track = root["track"] as? JsonObject ?: return emptyList()
        val title = (track["name"] as? JsonPrimitive)?.contentOrNull ?: return emptyList()
        val artist = (track["artists"] as? JsonArray)
            ?.mapNotNull { a -> ((a as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
            ?.joinToString(", ").orEmpty()
        val album = ((track["album"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
        val durationMs = (track["duration_ms"] as? JsonPrimitive)?.doubleOrNull

        val syncType = (root["syncType"] as? JsonPrimitive)?.contentOrNull
        val lines = root["lines"] as? JsonArray ?: return emptyList()
        val body = buildLyrics(lines, syncType == "LINE_SYNCED") ?: return emptyList()

        return listOf(
            RawHit(
                provider = Provider.SPOTIFY,
                title = title,
                artist = artist,
                album = album,
                durationSec = durationMs?.let { it / 1000.0 },
                synced = body.synced,
                plain = body.plain
            )
        )
    }

    private fun buildLyrics(lines: JsonArray, lineSynced: Boolean): LyricsBody? {
        val parsed = lines.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val words = (o["words"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            val ms = (o["startTimeMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
            ms to words
        }
        if (parsed.none { it.second.isNotBlank() }) return null

        if (lineSynced) {
            val lrc = parsed.joinToString("\n") { (ms, words) -> "${tag(ms)}$words" }
            if (LrcTools.isSynced(lrc)) return LyricsBody(synced = lrc, plain = null)
        }
        return LyricsBody(synced = null, plain = parsed.joinToString("\n") { it.second }.trim())
    }

    /** 16320 ms -> [00:16.32] */
    private fun tag(ms: Long): String {
        val cs = (ms / 10) % 100
        val s = (ms / 1000) % 60
        val m = ms / 60000
        return "[%02d:%02d.%02d]".format(m, s, cs)
    }
}
