package dev.koshik.lrcembedder.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(vm: MainViewModel) {
    val shown = remember(vm.songs, vm.search, vm.sortField, vm.ascending) {
        filterAndSort(vm.songs, vm.search, vm.sortField, vm.ascending)
    }
    val withLyrics = remember(vm.songs, vm.done) { vm.songs.count { it.path in vm.done } }
    val selecting = vm.selected.isNotEmpty()
    val ink = MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
/*------------------ Batch download removed, so rate limit not hit on simple use -----------------
        floatingActionButton = {
            if (vm.songs.isNotEmpty()) {
                val pulse by rememberInfiniteTransition(label = "fab").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "fabScale"
                )
                ExtendedFloatingActionButton(
                    onClick = { vm.startBatch() },
                    modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    text = {
                        Text(
                            if (selecting) "Fetch ${vm.selected.size} selected" else "Fetch all missing",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
*------------------- Batch Download Button -------*/
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    GradientText(
                        "LRC Embedder",
                        MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    Text(
                        "${vm.songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.showSettings = true }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }

            OutlinedTextField(
                value = vm.search,
                onValueChange = { vm.search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search songs or artists") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.search.isNotEmpty()) {
                        IconButton(onClick = { vm.search = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = ink.copy(alpha = 0.22f),
                    focusedContainerColor = ink.copy(alpha = 0.06f),
                    unfocusedContainerColor = ink.copy(alpha = 0.06f)
                )
            )

            // sort: direction button + field chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val angle by animateFloatAsState(if (vm.ascending) 0f else 180f, tween(300), label = "dirAngle")
                AssistChip(
                    onClick = { vm.flipDirection() },
                    label = { Text(directionLabel(vm.sortField, vm.ascending), fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = "Reverse order",
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(angle)
                        )
                    }
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (field in SortField.values()) {
                        FilterChip(
                            selected = vm.sortField == field,
                            onClick = { vm.chooseSort(field) },
                            label = { Text(field.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            if (selecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${vm.selected.size} selected", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.selectAll(shown) }) { Text("Select all") }
                    TextButton(onClick = { vm.clearSelection() }) { Text("Clear") }
                }
            }

            when {
                vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                shown.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (vm.songs.isEmpty()) "No music found on this device." else "No songs match your search.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { song ->
                        SongCard(
                            song = song,
                            done = song.path in vm.done,
                            selected = song.id in vm.selected,
                            onClick = { if (selecting) vm.toggleSelect(song) else vm.openSheet(song) },
                            onLongClick = { vm.toggleSelect(song) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    vm.sheet?.let { SongSheet(vm, it) }
    vm.batch?.let { BatchDialog(vm, it) }
    if (vm.showSettings) SettingsSheet(vm)
}
