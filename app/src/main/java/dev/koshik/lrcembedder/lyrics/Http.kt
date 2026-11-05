package dev.koshik.lrcembedder.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

object Http {
    val client = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
    }
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
}

/** Runs [block]; returns null on any failure except coroutine cancellation. */
suspend fun <T> safe(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** Retries transient failures with exponential backoff. Never swallows coroutine cancellation. */
suspend fun <T> withRetry(
    maxAttempts: Int = 2,
    baseDelayMs: Long = 400,
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempt++
            if (attempt >= maxAttempts) throw e
            kotlinx.coroutines.delay(baseDelayMs shl (attempt - 1))
        }
    }
}
