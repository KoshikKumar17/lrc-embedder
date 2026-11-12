package dev.koshik.lrcembedder.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.koshik.lrcembedder.data.Song
import dev.koshik.lrcembedder.lyrics.Provider

@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
) {
    Text(text, modifier = modifier, style = style.copy(brush = Brush.linearGradient(colors)))
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val ink = MaterialTheme.colorScheme.onSurface
    return this
        .clip(shape)
        .background(ink.copy(alpha = 0.07f), shape)
        .border(1.dp, ink.copy(alpha = 0.14f), shape)
}

@Composable
fun providerColor(p: Provider): Color = when (p) {
    Provider.LRCLIB -> MaterialTheme.colorScheme.tertiary
    Provider.NETEASE -> MaterialTheme.colorScheme.secondary
    Provider.QQ -> MaterialTheme.colorScheme.primary
    Provider.DEEZER -> MaterialTheme.colorScheme.inversePrimary
    Provider.SPOTIFY -> Color(0xFF1DB954)
    Provider.LRCRED -> Color(0xFFE5484D)
}

@Composable
private fun ArtPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongCard(
    song: Song,
    done: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }
    ) {
        val shape = RoundedCornerShape(20.dp)
        val primary = MaterialTheme.colorScheme.primary
        val ink = MaterialTheme.colorScheme.onSurface
        val bg by animateColorAsState(
            if (selected) primary.copy(alpha = 0.28f) else ink.copy(alpha = 0.07f),
            label = "cardBg"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bg, shape)
                .border(1.dp, if (selected) primary else ink.copy(alpha = 0.14f), shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = song.artUri,
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
                loading = { ArtPlaceholder() },
                error = { ArtPlaceholder() }
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.extension.isNotEmpty()) {
                        Pill(song.extension, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        song.artist.ifBlank { "Unknown artist" },
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedContent(targetState = if (selected) 2 else if (done) 1 else 0, label = "status") { st ->
                    when (st) {
                        2 -> Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = primary)
                        1 -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Has lyrics", tint = MaterialTheme.colorScheme.tertiary)
                        else -> Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ink.copy(alpha = 0.35f))
                    }
                }
                val totalSec = song.durationMs.coerceAtLeast(0L) / 1000L
                Pill(
                    String.format(java.util.Locale.ROOT, "%02d:%02d", totalSec / 60, totalSec % 60),
                    MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
