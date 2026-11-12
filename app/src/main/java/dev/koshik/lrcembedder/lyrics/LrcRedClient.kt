package dev.koshik.lrcembedder.lyrics

import dev.koshik.lrcembedder.BuildConfig
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

/**
 * lrc.red (https://lrc.red): free synced-lyrics API, keyed by ISRC.
 *
 * Terms: identify the client (User-Agent with site/app, link and contact email; X-Client-ID is the
 * same value for clients that can't set a User-Agent) and credit/link lrc.red wherever its data is
 * shown (see the lrc.red badge in the result card). The contact email comes from the LRCRED_EMAIL
 * build secret; when it is not set the API is still used, just without an email.
 */
object LrcRedClient {
    const val SITE_URL = "https://lrc.red"
    private const val SEARCH = "$SITE_URL/search.json"
    private const val APP_URL = "https://github.com/KoshikKumar17/lrc-embedder"

    private val email: String get() = BuildConfig.LRCRED_EMAIL.trim()
    val hasEmail: Boolean get() = email.isNotBlank()

    /** "LRCEmbedder/1.3 (https://github.com/...; mail@example.com)"; email omitted when no secret is set. */
    private val clientId: String
        get() = buildString {
            append("LRCEmbedder/").append(BuildConfig.VERSION_NAME).append(" (").append(APP_URL)
            if (hasEmail) append("; ").append(email)
            append(")")
        }

    suspend fun search(query: String): List<RawHit> {
        if (query.isBlank()) return emptyList()
        val resp = Http.client.get(SEARCH) {
            parameter("q", query.trim())
            header(HttpHeaders.UserAgent, clientId)
            header("X-Client-ID", clientId)
        }
        if (resp.status.value !in 200..299) return emptyList()
        val root = Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonObject
            ?: return emptyList()
        val hits = root["hits"] as? JsonArray ?: return emptyList()
        return hits.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val isrc = (o["isrc"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            RawHit(
                provider = Provider.LRCRED,
                title = title,
                artist = (o["artist"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                album = (o["album"] as? JsonPrimitive)?.contentOrNull,
                durationSec = (o["duration"] as? JsonPrimitive)?.doubleOrNull,
                fetch = { fetchLyrics(isrc) }
            )
        }
    }

    /** GET /s/{isrc}.lrc */
    private suspend fun fetchLyrics(isrc: String): LyricsBody? {
        val resp = Http.client.get("$SITE_URL/s/$isrc.lrc") {
            header(HttpHeaders.UserAgent, clientId)
            header("X-Client-ID", clientId)
        }
        if (resp.status.value !in 200..299) return null
        val text = LrcTools.normalize(resp.bodyAsText(Charsets.UTF_8))
        if (text.isBlank()) return null
        return if (LrcTools.isSynced(text)) LyricsBody(synced = text, plain = null)
        else LyricsBody(synced = null, plain = text)
    }
}
