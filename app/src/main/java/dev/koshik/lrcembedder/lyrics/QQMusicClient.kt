package dev.koshik.lrcembedder.lyrics

import android.util.Base64
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** QQ Music (y.qq.com): search for a song mid, then ask the lyric endpoint directly. */
object QQMusicClient {
    private const val SEARCH = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val LYRIC = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"

    /** Up to [limit] search hits; lyrics are fetched lazily for the ones the matcher likes. */
    suspend fun search(term: String, limit: Int = 10): List<RawHit> {
        if (term.isBlank()) return emptyList()
        return searchSongs(term, limit)
    }

    private suspend fun searchSongs(term: String, limit: Int): List<RawHit> {
        val resp = Http.client.get(SEARCH) {
            parameter("w", term)
            parameter("format", "json")
            parameter("inCharset", "utf8")
            parameter("outCharset", "utf8")
            parameter("platform", "yqq.json")
            parameter("new_json", 1)
            parameter("n", limit)
            parameter("p", 1)
            header("User-Agent", UA)
            header("Referer", "https://y.qq.com/")
        }
        if (resp.status.value !in 200..299) return emptyList()
        val root = parseJson(resp.bodyAsText(Charsets.UTF_8)) ?: return emptyList()
        val list = ((root["data"] as? JsonObject)?.get("song") as? JsonObject)?.get("list") as? JsonArray
            ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val mid = (o["mid"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val name = (o["title"] as? JsonPrimitive)?.contentOrNull
                ?: (o["name"] as? JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            val artists = (o["singer"] as? JsonArray)
                ?.mapNotNull { a -> ((a as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
                ?.joinToString(", ").orEmpty()
            val album = ((o["album"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
            val dur = (o["interval"] as? JsonPrimitive)?.doubleOrNull
            RawHit(
                provider = Provider.QQ,
                title = name,
                artist = artists,
                album = album,
                durationSec = dur,
                fetch = { fetchLyrics(mid) }
            )
        }
    }

    private suspend fun fetchLyrics(mid: String): LyricsBody? {
        val resp = Http.client.get(LYRIC) {
            parameter("songmid", mid)
            parameter("g_tk", 5381)
            parameter("format", "json")
            parameter("nobase64", 1)
            header("User-Agent", UA)
            header("Referer", "https://y.qq.com/")
        }
        if (resp.status.value !in 200..299) return null
        val root = parseJson(resp.bodyAsText(Charsets.UTF_8)) ?: return null
        var raw = (root["lyric"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null

        // some responses are still base64 even with nobase64=1
        if (!raw.trimStart().startsWith("[")) {
            raw = runCatching { String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(raw)
        }
        val text = LrcTools.normalize(decodeEntities(raw))
        if (text.isBlank()) return null
        val synced = LrcTools.isSynced(text)
        return LyricsBody(
            synced = if (synced) text else null,
            plain = if (synced) null else text
        )
    }

    private fun parseJson(body: String): JsonObject? {
        // be forgiving if the body is wrapped like callback({...})
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return Http.json.parseToJsonElement(body.substring(start, end + 1)) as? JsonObject
    }

    private val numEntity = Regex("""&#(\d+);""")

    /** QQ returns lyrics HTML-escaped (&#10; for newlines, &apos; ...). */
    private fun decodeEntities(s: String): String =
        numEntity.replace(s) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
}
