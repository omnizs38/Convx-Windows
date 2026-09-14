package com.convx.windows.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.windows.glass.GlassStyle

internal data class Album(
    val title: String,
    val artist: String,
    val start: Color,
    val end: Color,
)

internal val DemoAlbums: List<Album> = listOf(
    Album("Ambient Transmissions", "Marconi Union", Color(0xFFE05B7A), Color(0xFF7B2BE0)),
    Album("Outrun", "Kavinsky", Color(0xFF41C3F0), Color(0xFF1B4BE0)),
    Album("Movements", "Hummel", Color(0xFFF5B944), Color(0xFFE0542B)),
    Album("Tide", "Olafur Arnalds", Color(0xFF63E08A), Color(0xFF128A7B)),
    Album("Dive", "Tycho", Color(0xFFB98BFF), Color(0xFF3B2BE0)),
    Album("Isles", "Bicep", Color(0xFFFF8FB1), Color(0xFF8A1246)),
    Album("Substrata", "Biosphere", Color(0xFF7FD4FF), Color(0xFF2B5CE0)),
    Album("Immunity", "Jon Hopkins", Color(0xFFFFD27F), Color(0xFFE0762B)),
    Album("Cirrus", "Bonobo", Color(0xFF8FFFD8), Color(0xFF128A6B)),
    Album("Singularity", "Nosaj Thing", Color(0xFFD48BFF), Color(0xFF5B2BE0)),
    Album("Kiasmos", "Kiasmos", Color(0xFF9FB4FF), Color(0xFF2B34E0)),
    Album("Long Season", "Fishmans", Color(0xFFFFB38F), Color(0xFF8A3A12)),
)

private fun List<Album>.matching(query: String): List<Album> =
    if (query.isBlank()) this else filter {
        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
    }

private fun List<Track>.matching(query: String): List<Track> =
    if (query.isBlank()) this else filter {
        it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
    }

// -------------------------------------------------------------- shared building blocks

@Composable
internal fun ArtworkBox(start: Color, end: Color, corner: Dp, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(start, end))))
}

@Composable
internal fun Artwork(start: Color, end: Color, size: Dp, corner: Dp = 12.dp) {
    ArtworkBox(start, end, corner, Modifier.size(size))
}

@Composable
internal fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 32.dp, bottom = 14.dp),
    )
}

/**
 * Plain translucent panel rather than a glass one: content sits inside the recorded backdrop
 * subtree, and a glass surface there would sample the snapshot it is part of.
 */
@Composable
private fun ContentCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun EmptyState(query: String) {
    Text(
        "Nothing matches \"" + query + "\"",
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 28.dp),
    )
}

// ----------------------------------------------------------------------- album grid

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1.03f else 1f)

    Column(
        modifier
            .scale(lift)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
    ) {
        ArtworkBox(album.start, album.end, 16.dp, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(9.dp))
        Text(
            album.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.artist,
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Hand-built fixed-column grid instead of `LazyVerticalGrid`: the whole page lives in one
 * `verticalScroll` column so a single desktop scrollbar drives all of it.
 */
@Composable
private fun AlbumGrid(albums: List<Album>, columns: Int, onPlay: (Int) -> Unit) {
    albums.chunked(columns).forEachIndexed { rowIndex, rowAlbums ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            rowAlbums.forEachIndexed { columnIndex, album ->
                AlbumCard(
                    album = album,
                    onClick = { onPlay(rowIndex * columns + columnIndex) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(columns - rowAlbums.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ------------------------------------------------------------------------ track rows

@Composable
private fun TrackRow(position: Int, track: Track, isActive: Boolean, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered) Color.White.copy(alpha = 0.07f) else Color.Transparent,
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (position + 1).toString(),
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 12.sp,
            modifier = Modifier.width(26.dp),
        )
        Artwork(track.artStart, track.artEnd, 38.dp, 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1.6f)) {
            Text(
                track.title,
                color = if (isActive) AccentSoft else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            track.album,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatTime(track.durationSeconds.toFloat()),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun TrackList(player: PlayerState, query: String) {
    val tracks = player.queue.matching(query)
    if (tracks.isEmpty()) {
        EmptyState(query)
        return
    }
    tracks.forEach { track ->
        val position = player.queue.indexOf(track)
        TrackRow(
            position = position,
            track = track,
            isActive = position == player.index,
            onPlay = { player.play(position) },
        )
    }
}

// ----------------------------------------------------------------------------- pages

@Composable
internal fun HomePage(player: PlayerState, query: String) {
    val albums = DemoAlbums.matching(query).take(4)
    SectionHeader("Recently played")
    if (albums.isEmpty()) EmptyState(query) else AlbumGrid(albums, 4) { player.play(it) }
    SectionHeader("Up next")
    TrackList(player, query)
}

@Composable
internal fun ExplorePage(player: PlayerState, query: String) {
    val albums = DemoAlbums.matching(query)
    SectionHeader("New releases")
    if (albums.isEmpty()) EmptyState(query) else AlbumGrid(albums.take(8), 4) { player.play(it) }
    SectionHeader("Charts")
    TrackList(player, query)
}

@Composable
internal fun LibraryPage(player: PlayerState, query: String) {
    val albums = DemoAlbums.matching(query)
    SectionHeader("Albums")
    if (albums.isEmpty()) EmptyState(query) else AlbumGrid(albums, 5) { player.play(it) }
    SectionHeader("Songs")
    TrackList(player, query)
}

// -------------------------------------------------------------------- glass settings

@Composable
internal fun GlassSettingsPage(settings: GlassSettings, onChange: (GlassSettings) -> Unit) {
    SectionHeader("Material")
    ContentCard {
        StyleSelector(settings.style) { onChange(settings.copy(style = it)) }
        Spacer(Modifier.height(10.dp))
        SettingSlider("Vibrancy", settings.vibrancy, 0f, 2f) { onChange(settings.copy(vibrancy = it)) }
        SettingSlider("Blur radius (dp)", settings.blurRadius, 0f, 24f) { onChange(settings.copy(blurRadius = it)) }
        SettingSlider("Surface opacity", settings.surfaceOpacity, 0f, 1f) { onChange(settings.copy(surfaceOpacity = it)) }
        SettingSlider("Sheen opacity", settings.sheenOpacity, 0f, 0.4f) { onChange(settings.copy(sheenOpacity = it)) }
    }

    SectionHeader("Lens")
    ContentCard {
        SettingSlider("Lens height", settings.lensHeight, 0f, 1f) { onChange(settings.copy(lensHeight = it)) }
        SettingSlider("Lens amount", settings.lensAmount, 0f, 1f) { onChange(settings.copy(lensAmount = it)) }
        SettingSlider("Depth effect", settings.depthEffect, 0f, 1f) { onChange(settings.copy(depthEffect = it)) }
        SettingSlider("Chromatic aberration", settings.chromaticAberration, 0f, 1f) {
            onChange(settings.copy(chromaticAberration = it))
        }
    }

    SectionHeader("Highlight")
    ContentCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Animate specular rim", color = Color.White, fontSize = 13.sp)
                Text(
                    "Drifts the rim highlight angle over a nine second cycle.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = settings.animateHighlight,
                onCheckedChange = { onChange(settings.copy(animateHighlight = it)) },
            )
        }
    }

    SectionHeader("Shortcuts")
    ContentCard {
        ShortcutRow("Space", "Play / pause")
        ShortcutRow("Media keys", "Play, next, previous")
        ShortcutRow("Esc", "Close Now Playing")
        ShortcutRow("Click artwork", "Open Now Playing")
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ShortcutRow(keys: String, action: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(104.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(keys, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(action, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    from: Float,
    to: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
            Text(
                "%.2f".format(value),
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 12.sp,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = from..to,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StyleSelector(selected: GlassStyle, onSelect: (GlassStyle) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassStyle.values().forEach { style ->
            StyleChip(
                label = style.name.lowercase().replaceFirstChar { it.uppercase() },
                selected = style == selected,
                onClick = { onSelect(style) },
            )
        }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Accent
            hovered -> Color.White.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.08f)
        },
    )

    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
