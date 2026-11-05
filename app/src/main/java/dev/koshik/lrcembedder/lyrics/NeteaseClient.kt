package dev.koshik.lrcembedder.lyrics

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Netease Cloud Music (music.163.com) search + lyrics. Request shape follows syncedlyrics. */
object NeteaseClient {
    private const val BASE = "https://music.163.com/api"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private const val COOKIE =
            "NMTID=00OAVK3xqDG726ITU6jopU6jF2yMk0AAAGCO8l1BA; " +
            "JSESSIONID-WYYY=8KQo11YK2GZP45RMlz8Kn80vHZ9%2FGvwzRKQXXy0iQoFKycWdBlQjbfT0MJrFa6hwRfmpfBYKeHliUPH287JC3hNW99WQjrh9b9RmKT%2Fg1Exc2VwHZcsqi7ITxQgfEiee50po28x5xTTZXKoP%2FRMctN2jpDeg57kdZrXz%2FD%2FWghb%5C4DuZ%3A1659124633932; " +
            "_iuqxldmzr_=32; " +
            "_ntes_nnid=0db6667097883aa9596ecfe7f188c3ec,1659122833973; " +
            "_ntes_nuid=0db6667097883aa9596ecfe7f188c3ec; " +
            "WNMCID=xygast.1659122837568.01.0; " +
            "WEVNSM=1.0.0; " +
            "WM_NI=CwbjWAFbcIzPX3dsLP%2F52VB%2Bxr572gmqAYwvN9KU5X5f1nRzBYl0SNf%2BV9FTmmYZy%2FoJLADaZS0Q8TrKfNSBNOt0HLB8rRJh9DsvMOT7%2BCGCQLbvlWAcJBJeXb1P8yZ3RHA%3D; " +
            "WM_NIKE=9ca17ae2e6ffcda170e2e6ee90c65b85ae87b9aa5483ef8ab3d14a939e9a83c459959caeadce47e991fbaee82af0fea7c3b92a81a9ae8bd64b86beadaaf95c9cedac94cf5cedebfeb7c121bcaefbd8b16dafaf8fbaf67e8ee785b6b854f7baff8fd1728287a4d1d246a6f59adac560afb397bbfc25ad9684a2c76b9a8d00b2bb60b295aaafd24a8e91bcd1cb4882e8beb3c964fb9cbd97d04598e9e5a4c6499394ae97ef5d83bd86a3c96f9cbeffb1bb739aed9ea9c437e2a3; " +
            "WM_TID=AAkRFnl03RdABEBEQFOBWHCPOeMra4IL; " +
            "playerid=94262567"

    /** Up to [limit] search hits; lyrics are fetched lazily for the ones the matcher likes. */
    suspend fun search(term: String, limit: Int = 8): List<RawHit> {
        if (term.isBlank()) return emptyList()
        return searchSongs(term, limit)
    }

    private suspend fun searchSongs(term: String, limit: Int): List<RawHit> {
        val resp = Http.client.get("$BASE/search/pc") {
            parameter("s", term)
            parameter("type", 1)
            parameter("limit", limit)
            parameter("offset", 0)
            applyHeaders()
        }
        if (resp.status.value !in 200..299) return emptyList()
        val root = Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonObject
            ?: return emptyList()
        val songs = (root["result"] as? JsonObject)?.get("songs") as? JsonArray ?: return emptyList()
        return songs.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val artists = (o["artists"] as? JsonArray)
                ?.mapNotNull { a -> ((a as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
                ?.joinToString(", ").orEmpty()
            val album = ((o["album"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
            val ms = (o["duration"] as? JsonPrimitive)?.doubleOrNull
                ?: (o["dt"] as? JsonPrimitive)?.doubleOrNull
            RawHit(
                provider = Provider.NETEASE,
                title = name,
                artist = artists,
                album = album,
                durationSec = ms?.let { it / 1000.0 },
                fetch = { fetchLyrics(id) }
            )
        }
    }

    private suspend fun fetchLyrics(id: Long): LyricsBody? {
        val resp = Http.client.get("$BASE/song/lyric") {
            parameter("id", id)
            parameter("lv", 1)
            applyHeaders()
        }
        if (resp.status.value !in 200..299) return null
        val root = Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonObject
            ?: return null
        val raw = ((root["lrc"] as? JsonObject)?.get("lyric") as? JsonPrimitive)?.contentOrNull
            ?: return null
        val text = LrcTools.normalize(raw)
        if (text.isBlank()) return null
        val synced = LrcTools.isSynced(text)
        return LyricsBody(
            synced = if (synced) text else null,
            plain = if (synced) null else text
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders() {
        header("Accept", "application/json")
        header("Accept-Language", "en-US,en;q=0.9")
        header("Cookie", COOKIE)
        header("User-Agent", UA)
    }
}
