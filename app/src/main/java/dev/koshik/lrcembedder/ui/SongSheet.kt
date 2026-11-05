package dev.koshik.lrcembedder.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.koshik.lrcembedder.lyrics.LrcTools
import dev.koshik.lrcembedder.lyrics.LyricsResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSheet(vm: MainViewModel, s: SheetUi) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.closeSheet() },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        s.song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(s.song.artist, s.song.extension).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Column {
                    OutlinedTextField(
                        value = s.title,
                        onValueChange = { s.title = it },
                        label = { Text("Song name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = s.artist,
                        onValueChange = { s.artist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.runSearch(s) },
                        enabled = !s.searching,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Search again")
                    }
                }
            }
            if (s.searching) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            s.message?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                }
            }
            if (s.searched && !s.searching && s.results.isEmpty()) {
                item {
                    Text(
                        "No lyrics found on LRCLib, Netease or QQ Music. Try editing the name or artist above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(s.results) { r ->
                ResultCard(r = r, busy = s.busy, onEmbed = { vm.embedResult(s, r) })
            }
        }
    }
}

private fun formatDuration(sec: Double?): String =
    if (sec == null || sec <= 0) "" else "%d:%02d".format(sec.toInt() / 60, sec.toInt() % 60)

@Composable
private fun ResultCard(r: LyricsResult, busy: Boolean, onEmbed: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val preview = remember(r) { LrcTools.toPlain(r.best.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass()
            .animateContentSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(r.provider.label, providerColor(r.provider))
            Spacer(Modifier.width(6.dp))
            Pill(
                if (r.isSynced) "Synced" else "Plain",
                if (r.isSynced) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(r.durationSec),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            r.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOf(r.artist, r.album.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (expanded) preview else preview.lines().take(4).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Preview") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onEmbed, enabled = !busy) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Embed")
            }
        }
    }
}
