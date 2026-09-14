package com.convx.windows.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.windows.glass.GlassEffectConfig
import com.convx.windows.glass.GlassStyle
import com.convx.windows.glass.LayerBackdrop
import com.convx.windows.glass.LocalAppBackdrop
import com.convx.windows.glass.LocalGlassEffectConfig
import com.convx.windows.glass.layerBackdrop
import com.convx.windows.glass.liquidGlass
import com.convx.windows.glass.rememberLayerBackdrop
import kotlinx.coroutines.delay

/*
 * Desktop chrome metrics.
 *
 * The layout is the standard desktop three-region shell - persistent left sidebar, top bar,
 * full-width player bar - rather than the phone arrangement of a floating bottom nav bar.
 * All three are glass surfaces, so they are siblings drawn *after* the content layer: a
 * glass surface nested inside the recorded subtree would sample the snapshot it is part of.
 */
internal val ChromeGap = 12.dp
internal val SidebarWidth = 240.dp
internal val TopBarHeight = 56.dp
internal val PlayerBarHeight = 92.dp

internal val ContentStartInset = ChromeGap + SidebarWidth + ChromeGap
internal val ContentTopInset = ChromeGap + TopBarHeight + ChromeGap
internal val ContentBottomInset = ChromeGap + PlayerBarHeight + ChromeGap

internal val Accent = Color(0xFF8B5CF6)
internal val AccentSoft = Color(0xFFB794FF)

enum class Section(val label: String, val title: String, val subtitle: String) {
    Home("Listen Now", "Listen Now", "Picked up where you left off"),
    Explore("Explore", "Explore", "New releases and charts"),
    Library("Your Library", "Your Library", "Everything you have saved"),
    Settings("Glass & Playback", "Glass & Playback", "Every Liquid Glass parameter, live"),
}

/** Mutable mirror of [GlassEffectConfig] so the settings page can drive the material live. */
data class GlassSettings(
    val style: GlassStyle = GlassStyle.LIQUID,
    val vibrancy: Float = 1.2f,
    val blurRadius: Float = 2f,
    val lensHeight: Float = 0.4f,
    val lensAmount: Float = 0.6f,
    val depthEffect: Float = 0f,
    val chromaticAberration: Float = 0f,
    val surfaceOpacity: Float = 0.5f,
    val sheenOpacity: Float = 0.08f,
    val animateHighlight: Boolean = true,
) {
    fun toConfig(): GlassEffectConfig = GlassEffectConfig(
        style = style,
        vibrancy = vibrancy,
        blurRadius = blurRadius,
        lensHeight = lensHeight,
        lensAmount = lensAmount,
        depthEffect = depthEffect,
        chromaticAberration = chromaticAberration,
        surfaceOpacity = surfaceOpacity,
        sheenOpacity = sheenOpacity,
        animateHighlight = animateHighlight,
    )
}

private val ConvxColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Color(0xFF05050A),
    surface = Color(0xFF0A0A12),
    onSurface = Color.White,
)

/** Thin overlay scrollbar, sized for a mouse rather than a thumb. */
private val ConvxScrollbarStyle = ScrollbarStyle(
    minimalHeight = 36.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 240,
    unhoverColor = Color.White.copy(alpha = 0.12f),
    hoverColor = Color.White.copy(alpha = 0.30f),
)

@Composable
fun ConvxApp(player: PlayerState) {
    val backdrop = rememberLayerBackdrop()
    var settings by remember { mutableStateOf(GlassSettings()) }
    var section by remember { mutableStateOf(Section.Home) }
    var query by remember { mutableStateOf("") }
    val config = settings.toConfig()

    // Stands in for a real playback clock until a JVM audio backend lands.
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            delay(250L)
            player.tick(0.25f)
        }
    }

    MaterialTheme(colorScheme = ConvxColorScheme) {
        CompositionLocalProvider(
            LocalAppBackdrop provides backdrop,
            LocalGlassEffectConfig provides config,
            LocalScrollbarStyle provides ConvxScrollbarStyle,
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF05050A))) {
                ContentLayer(
                    backdrop = backdrop,
                    needsBackdrop = { config.needsBackdrop },
                    section = section,
                    query = query,
                    player = player,
                    settings = settings,
                    onSettingsChange = { settings = it },
                )

                Sidebar(
                    selected = section,
                    onSelect = { section = it },
                    modifier = Modifier.align(Alignment.TopStart),
                )
                TopBar(
                    section = section,
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.align(Alignment.TopStart),
                )
                PlayerBar(player, Modifier.align(Alignment.BottomStart))

                if (player.isNowPlayingExpanded) {
                    NowPlayingOverlay(player, settings.blurRadius)
                }
            }
        }
    }
}

/**
 * The scrolling content, and the only thing recorded into the backdrop. Insets keep it clear
 * of the glass chrome that floats above it.
 */
@Composable
private fun ContentLayer(
    backdrop: LayerBackdrop,
    needsBackdrop: () -> Boolean,
    section: Section,
    query: String,
    player: PlayerState,
    settings: GlassSettings,
    onSettingsChange: (GlassSettings) -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3B1E5A), Color(0xFF16284A), Color(0xFF0A0A12)),
                    start = Offset.Zero,
                    end = Offset(1700f, 1150f),
                ),
            )
            .layerBackdrop(backdrop, enabled = needsBackdrop),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = ContentStartInset,
                    top = ContentTopInset,
                    end = 30.dp,
                    bottom = ContentBottomInset,
                ),
        ) {
            PageHeader(section.title, section.subtitle)
            when (section) {
                Section.Home -> HomePage(player, query)
                Section.Explore -> ExplorePage(player, query)
                Section.Library -> LibraryPage(player, query)
                Section.Settings -> GlassSettingsPage(settings, onSettingsChange)
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = ContentTopInset, bottom = ContentBottomInset, end = 8.dp),
        )
    }
}

@Composable
private fun Sidebar(
    selected: Section,
    onSelect: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(ChromeGap)
            .width(SidebarWidth)
            .fillMaxHeight()
            .liquidGlass(
                shape = RoundedCornerShape(22.dp),
                blurRadiusDp = 3f,
                highlightAlpha = 0.40f,
            )
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Text("Convx", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "for Windows",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(26.dp))

        Section.values().forEach { item ->
            NavItem(
                label = item.label,
                selected = item == selected,
                onClick = { onSelect(item) },
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Space  play / pause",
            color = Color.White.copy(alpha = 0.32f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "v1.0.0 \u00B7 GPL-3.0",
            color = Color.White.copy(alpha = 0.32f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Color.White.copy(alpha = 0.16f)
            hovered -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Accent else Color.Transparent),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TopBar(
    section: Section,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(start = ContentStartInset, top = ChromeGap, end = ChromeGap)
            .fillMaxWidth()
            .height(TopBarHeight)
            .liquidGlass(shape = RoundedCornerShape(18.dp), blurRadiusDp = 3f)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            section.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(24.dp))
        SearchField(query, onQueryChange, Modifier.weight(1f))
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        Color.White.copy(alpha = if (hovered) 0.13f else 0.07f),
    )

    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Text)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                "Search songs, albums and artists",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 13.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
