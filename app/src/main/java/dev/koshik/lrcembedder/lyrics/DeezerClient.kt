package dev.koshik.lrcembedder.lyrics

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Deezer: public search (title, artist, length) + the synced lyrics its web player shows to signed-out
 * visitors. No key needed: the anonymous player token is fetched on demand and reused for a few minutes.
 * Strong on music the other catalogues miss.
 */
object DeezerClient {
    private const val API = "https://api.deezer.com"
    private const val PIPE = "https://pipe.deezer.com/api"
    private const val AUTH = "https://auth.deezer.com/login/anonymous"
    private const val TOKEN_REUSE_MS = 5 * 60 * 1000L
    private const val LYRICS_QUERY =
        "query SynchronizedTrackLyrics(\$trackId: String!) { track(trackId: \$trackId) { lyrics { synchronizedLines { lrcTimestamp line } } } }"

    private val timestamp = Regex("""^\[\d{1,3}:\d{2}(\.\d{1,3})?]$""")

    private val tokenLock = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt = 0L

    suspend fun search(query: String, limit: Int = 8): List<RawHit> {
        if (query.isBlank()) return emptyList()
        val resp = Http.client.get("$API/search") {
            parameter("q", query)
            parameter("limit", limit)
        }
        if (resp.status.value !in 200..299) return emptyList()
        val root = Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonObject
            ?: return emptyList()
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val artist = ((o["artist"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val album = ((o["album"] as? JsonObject)?.get("title") as? JsonPrimitive)?.contentOrNull
            val dur = (o["duration"] as? JsonPrimitive)?.doubleOrNull
            RawHit(
                provider = Provider.DEEZER,
                title = title,
                artist = artist,
                album = album,
                durationSec = dur,
                fetch = { fetchLyrics(id) }
            )
        }
    }

    private suspend fun fetchLyrics(id: Long): LyricsBody? {
        val body = buildJsonObject {
            put("operationName", "SynchronizedTrackLyrics")
            putJsonObject("variables") { put("trackId", id.toString()) }
            put("query", LYRICS_QUERY)
        }.toString()

        // A token can be refused before its stated expiry; one fresh token is worth a single retry.
        repeat(2) { attempt ->
            val token = token(forceNew = attempt > 0) ?: return null
            val resp = Http.client.post(PIPE) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(body)
            }
            if (resp.status.value == 401) return@repeat
            if (resp.status.value !in 200..299) return null
            val lines = runCatching {
                val root = Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as JsonObject
                val track = (root["data"] as? JsonObject)?.get("track") as? JsonObject
                val lyrics = track?.get("lyrics") as? JsonObject
                lyrics?.get("synchronizedLines") as? JsonArray
            }.getOrNull() ?: return null
            val lrc = toLrc(lines) ?: return null
            return LyricsBody(synced = lrc, plain = null)
        }
        return null
    }

    private fun toLrc(lines: JsonArray): String? {
        val out = lines.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val stamp = (o["lrcTimestamp"] as? JsonPrimitive)?.contentOrNull?.trim() ?: return@mapNotNull null
            if (!timestamp.matches(stamp)) return@mapNotNull null
            stamp + ((o["line"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
        val text = out.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: return null
        val normalized = LrcTools.normalize(text)
        return normalized.takeIf { LrcTools.isSynced(it) }
    }

    private suspend fun token(forceNew: Boolean): String? = tokenLock.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (!forceNew && cached != null && now < tokenExpiresAt) return cached
        val resp = Http.client.get(AUTH) {
            parameter("jo", "p")
            parameter("rto", "c")
            parameter("i", "c")
        }
        if (resp.status.value !in 200..299) return null
        val jwt = runCatching {
            ((Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as JsonObject)["jwt"] as? JsonPrimitive)?.contentOrNull
        }.getOrNull() ?: return null
        cachedToken = jwt
        tokenExpiresAt = now + TOKEN_REUSE_MS
        jwt
    }
}
