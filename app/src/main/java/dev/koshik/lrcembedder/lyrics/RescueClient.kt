package dev.koshik.lrcembedder.lyrics

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * "Last resort" canonicalizer, not a lyrics source. iTunes and Deezer have forgiving fuzzy search, so a
 * garbled query like "🔥 Ed Sheeran - 🍕Shape of You🍕 [FREE]" turns into clean "Ed Sheeran / Shape of You"
 * pairs that LRCLib can then find.
 */
object RescueClient {
    data class Meta(val title: String, val artist: String)

    suspend fun canonicalize(query: String, limit: Int = 3): List<Meta> {
        if (query.isBlank()) return emptyList()
        val (itunes, deezer) = coroutineScope {
            val i = async { safe { itunes(query, limit) } ?: emptyList() }
            val d = async { safe { deezer(query, limit) } ?: emptyList() }
            i.await() to d.await()
        }
        val out = LinkedHashMap<String, Meta>()
        for (m in itunes + deezer) out.putIfAbsent("${m.artist.lowercase()}|${m.title.lowercase()}", m)
        return out.values.toList()
    }

    private suspend fun itunes(query: String, limit: Int): List<Meta> {
        val resp = Http.client.get("https://itunes.apple.com/search") {
            parameter("term", query.trim())
            parameter("entity", "song")
            parameter("limit", limit)
        }
        if (resp.status.value !in 200..299) return emptyList()
        val results = (Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonObject)
            ?.get("results") as? JsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val t = (o["trackName"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val a = (o["artistName"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            Meta(t, a)
        }
    }

    private suspend fun deezer(query: String, limit: Int): List<Meta> {
        val resp = Http.client.get("https://api.deezer.com/search") {
            parameter("q", query.trim())
            parameter("limit", limit)
        }
        if (resp.status.value !in 200..299) return emptyList()
        val data = (Http.json.parseToJsonElement(resp.bodyAsText(Charsets.UTF_8)) as? JsonObject)
            ?.get("data") as? JsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val t = (o["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val a = ((o["artist"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Meta(t, a)
        }
    }
}
