package dev.koshik.lrcembedder.ui

import android.app.Application
import android.media.MediaScannerConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.koshik.lrcembedder.data.Access
import dev.koshik.lrcembedder.data.LyricsEmbedder
import dev.koshik.lrcembedder.data.Prefs
import dev.koshik.lrcembedder.data.Song
import dev.koshik.lrcembedder.data.SongRepository
import dev.koshik.lrcembedder.lyrics.LyricsResult
import dev.koshik.lrcembedder.lyrics.LyricsService
import dev.koshik.lrcembedder.lyrics.QueryCleaner
import dev.koshik.lrcembedder.ui.theme.Palette
import dev.koshik.lrcembedder.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortField(val label: String, val defaultAscending: Boolean) {
    TITLE("Title", true),
    ARTIST("Artist", true),
    RECENT("Date added", false),
    DURATION("Length", true)
}

fun directionLabel(f: SortField, ascending: Boolean): String = when (f) {
    SortField.TITLE, SortField.ARTIST -> if (ascending) "A → Z" else "Z → A"
    SortField.RECENT -> if (ascending) "Oldest first" else "Newest first"
    SortField.DURATION -> if (ascending) "Shortest first" else "Longest first"
}

/** State of the per-song search sheet. */
class SheetUi(val song: Song, val initialTitle: String, val initialArtist: String) {
    var title by mutableStateOf(initialTitle)
    var artist by mutableStateOf(initialArtist)
    var results by mutableStateOf<List<LyricsResult>>(emptyList())
    var searching by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
}

/** State of the "fetch all" progress dialog. */
class BatchUi(val total: Int) {
    var index by mutableIntStateOf(0)
    var ok by mutableIntStateOf(0)
    var missing by mutableIntStateOf(0)
    var failed by mutableIntStateOf(0)
    var current by mutableStateOf("")
    var running by mutableStateOf(true)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)

    var hasAccess by mutableStateOf(Access.has(ctx))
        private set
    var loading by mutableStateOf(false)
        private set
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var done by mutableStateOf(prefs.done())
        private set
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set
    var alsoLrc by mutableStateOf(prefs.alsoSaveLrc)
        private set

    var search by mutableStateOf("")

    var sortField by mutableStateOf(
        runCatching { SortField.valueOf(prefs.sortName) }.getOrDefault(SortField.TITLE)
    )
        private set
    var ascending by mutableStateOf(prefs.ascending)
        private set

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.themeName) }.getOrDefault(ThemeMode.SYSTEM)
    )
        private set
    var palette by mutableStateOf(
        runCatching { Palette.valueOf(prefs.paletteName) }.getOrDefault(Palette.AURORA)
    )
        private set
    var animatedBg by mutableStateOf(prefs.animated)
        private set
    var showSettings by mutableStateOf(false)

    var sheet by mutableStateOf<SheetUi?>(null)
        private set
    var batch by mutableStateOf<BatchUi?>(null)
        private set

    private var batchJob: Job? = null

    // ---- access + library -------------------------------------------------------------

    fun refreshAccess() {
        val now = Access.has(ctx)
        hasAccess = now
        if (now && songs.isEmpty() && !loading) loadSongs()
    }

    fun loadSongs() {
        viewModelScope.launch {
            loading = true
            songs = try {
                withContext(Dispatchers.IO) { SongRepository.load(ctx) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            loading = false
        }
    }

    fun updateAlsoLrc(v: Boolean) {
        alsoLrc = v
        prefs.alsoSaveLrc = v
    }

    // ---- sorting + look & feel ------------------------------------------------------

    /** Tapping the current field flips the direction, a new field starts at its natural direction. */
    fun chooseSort(field: SortField) {
        if (field == sortField) {
            ascending = !ascending
        } else {
            sortField = field
            ascending = field.defaultAscending
        }
        prefs.sortName = sortField.name
        prefs.ascending = ascending
    }

    fun flipDirection() {
        ascending = !ascending
        prefs.ascending = ascending
    }

    fun changeTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.themeName = mode.name
    }

    fun changePalette(p: Palette) {
        palette = p
        prefs.paletteName = p.name
    }

    fun changeAnimated(v: Boolean) {
        animatedBg = v
        prefs.animated = v
    }

    // ---- selection ------------------------------------------------------------------

    fun toggleSelect(song: Song) {
        selected = if (song.id in selected) selected - song.id else selected + song.id
    }

    fun selectAll(list: List<Song>) {
        selected = list.map { it.id }.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    // ---- single song sheet ------------------------------------------------------------

    fun openSheet(song: Song) {
        val q = QueryCleaner.fromSong(song)
        val s = SheetUi(song, q.title, q.artist)
        sheet = s
        runSearch(s)
    }

    fun closeSheet() {
        sheet = null
    }

    fun runSearch(s: SheetUi) {
        viewModelScope.launch {
            s.searching = true
            s.message = null
            // untouched text: use the full song reading (file name, tags); edited text: search exactly that
            val q = if (s.title == s.initialTitle && s.artist == s.initialArtist) {
                QueryCleaner.fromSong(s.song)
            } else {
                QueryCleaner.manual(s.title, s.artist, s.song.durationMs / 1000.0)
            }
            s.results = try {
                LyricsService.searchAll(q).distinctBy { it.key }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            s.searching = false
            s.searched = true
        }
    }

    fun embedResult(s: SheetUi, r: LyricsResult) {
        val lyrics = r.best ?: return
        viewModelScope.launch {
            s.busy = true
            s.message = "Writing lyrics…"
            val out = withContext(Dispatchers.IO) {
                LyricsEmbedder.embed(s.song.path, lyrics, alsoLrc)
            }
            if (out.embedded || out.lrcFileSaved) markDone(s.song.path)
            rescan(s.song.path)
            s.message = when {
                out.embedded && out.lrcFileSaved -> "Embedded in ${s.song.extension} and saved .lrc"
                out.embedded -> "Lyrics embedded in ${s.song.extension}"
                out.lrcFileSaved -> "This format can't hold tags, saved a .lrc file next to the song instead"
                else -> "Failed: ${out.error ?: "unknown error"}"
            }
            s.busy = false
        }
    }

    // ---- batch ------------------------------------------------------------------------

    fun startBatch() {
        if (batchJob?.isActive == true) return
        val targets = if (selected.isNotEmpty()) {
            songs.filter { it.id in selected }
        } else {
            songs.filter { it.path !in done }
        }
        val b = BatchUi(targets.size)
        batch = b
        if (targets.isEmpty()) {
            b.running = false
            return
        }
        batchJob = viewModelScope.launch {
            try {
                for (song in targets) {
                    if (!isActive) break
                    b.current = song.title
                    try {
                        val best = LyricsService.findBest(QueryCleaner.fromSong(song))
                        val lyrics = best?.best
                        if (lyrics == null) {
                            b.missing++
                        } else {
                            val out = withContext(Dispatchers.IO) {
                                LyricsEmbedder.embed(song.path, lyrics, alsoLrc)
                            }
                            if (out.embedded || out.lrcFileSaved) {
                                b.ok++
                                markDone(song.path)
                                rescan(song.path)
                            } else {
                                b.failed++
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        b.failed++
                    }
                    b.index++
                    delay(120)
                }
            } finally {
                b.running = false
            }
        }
    }

    fun stopBatch() {
        batchJob?.cancel()
    }

    fun closeBatch() {
        batch = null
        selected = emptySet()
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun markDone(path: String) {
        done = done + path
        prefs.saveDone(done)
    }

    private fun rescan(path: String) {
        MediaScannerConnection.scanFile(ctx, arrayOf(path), null, null)
    }
}

fun filterAndSort(songs: List<Song>, query: String, field: SortField, ascending: Boolean): List<Song> {
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) songs else songs.filter {
        it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
    }
    val sorted = when (field) {
        SortField.TITLE -> filtered.sortedBy { it.title.lowercase() }
        SortField.ARTIST -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        SortField.RECENT -> filtered.sortedBy { it.dateAdded }
        SortField.DURATION -> filtered.sortedBy { it.durationMs }
    }
    return if (ascending) sorted else sorted.reversed()
}
