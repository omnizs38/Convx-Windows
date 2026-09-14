package com.convx.windows.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.windows.glass.PLAYER_BLUR_MULTIPLIER
import com.convx.windows.glass.liquidGlass

/**
 * Transport glyphs are drawn as vectors rather than typed as font characters. A desktop build
 * cannot assume any particular symbol font is installed, and vectors stay crisp at every
 * Windows display scale.
 */
internal enum class Glyph { Play, Pause, Previous, Next, Shuffle, Repeat, RepeatOne, Volume, Muted, Close }

@Composable
internal fun GlyphIcon(glyph: Glyph, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val unit = if (w < h) w else h
        val line = Stroke(width = unit * 0.13f, cap = StrokeCap.Round)

        when (glyph) {
            Glyph.Play -> drawPath(
                Path().apply {
                    moveTo(w * 0.26f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.50f)
                    lineTo(w * 0.26f, h * 0.86f)
                    close()
                },
                color,
            )

            Glyph.Pause -> {
                val barWidth = w * 0.17f
                val radius = CornerRadius(barWidth * 0.40f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.26f, h * 0.16f),
                    size = Size(barWidth, h * 0.68f),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.57f, h * 0.16f),
                    size = Size(barWidth, h * 0.68f),
                    cornerRadius = radius,
                )
            }

            Glyph.Previous -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.16f, h * 0.18f),
                    size = Size(w * 0.10f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.04f),
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.86f, h * 0.16f)
                        lineTo(w * 0.34f, h * 0.50f)
                        lineTo(w * 0.86f, h * 0.84f)
                        close()
                    },
                    color,
                )
            }

            Glyph.Next -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.14f, h * 0.16f)
                        lineTo(w * 0.66f, h * 0.50f)
                        lineTo(w * 0.14f, h * 0.84f)
                        close()
                    },
                    color,
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.74f, h * 0.18f),
                    size = Size(w * 0.10f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.04f),
                )
            }

            Glyph.Shuffle -> {
                drawLine(
                    color = color,
                    start = Offset(w * 0.16f, h * 0.26f),
                    end = Offset(w * 0.84f, h * 0.74f),
                    strokeWidth = unit * 0.13f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.16f, h * 0.74f),
                    end = Offset(w * 0.84f, h * 0.26f),
                    strokeWidth = unit * 0.13f,
                    cap = StrokeCap.Round,
                )
            }

            Glyph.Repeat -> drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.14f, h * 0.20f),
                size = Size(w * 0.72f, h * 0.60f),
                cornerRadius = CornerRadius(unit * 0.24f),
                style = line,
            )

            Glyph.RepeatOne -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.14f, h * 0.20f),
                    size = Size(w * 0.72f, h * 0.60f),
                    cornerRadius = CornerRadius(unit * 0.24f),
                    style = line,
                )
                drawCircle(color = color, radius = unit * 0.09f, center = Offset(w * 0.5f, h * 0.5f))
            }

            Glyph.Volume -> {
                drawPath(speakerPath(w, h), color)
                drawArc(
                    color = color,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.40f, h * 0.26f),
                    size = Size(w * 0.34f, h * 0.48f),
                    style = line,
                )
                drawArc(
                    color = color,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.38f, h * 0.12f),
                    size = Size(w * 0.54f, h * 0.76f),
                    style = line,
                )
            }

            Glyph.Muted -> {
                drawPath(speakerPath(w, h), color)
                drawLine(
                    color = color,
                    start = Offset(w * 0.62f, h * 0.36f),
                    end = Offset(w * 0.90f, h * 0.64f),
                    strokeWidth = unit * 0.12f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.62f, h * 0.64f),
                    end = Offset(w * 0.90f, h * 0.36f),
                    strokeWidth = unit * 0.12f,
                    cap = StrokeCap.Round,
                )
            }

            Glyph.Close -> {
                drawLine(
                    color = color,
                    start = Offset(w * 0.24f, h * 0.24f),
                    end = Offset(w * 0.76f, h * 0.76f),
                    strokeWidth = unit * 0.13f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.24f, h * 0.76f),
                    end = Offset(w * 0.76f, h * 0.24f),
                    strokeWidth = unit * 0.13f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun speakerPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.10f, h * 0.36f)
    lineTo(w * 0.28f, h * 0.36f)
    lineTo(w * 0.48f, h * 0.16f)
    lineTo(w * 0.48f, h * 0.84f)
    lineTo(w * 0.28f, h * 0.64f)
    lineTo(w * 0.10f, h * 0.64f)
    close()
}

/** Circular icon button with the hover feedback and hand cursor a desktop user expects. */
@Composable
internal fun GlyphButton(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 32.dp,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint by animateColorAsState(
        when {
            active -> AccentSoft
            hovered -> Color.White
            else -> Color.White.copy(alpha = 0.72f)
        },
    )
    val background by animateColorAsState(
        if (hovered) Color.White.copy(alpha = 0.12f) else Color.Transparent,
    )

    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Modifier.size(diameter * 0.46f), tint)
    }
}

@Composable
private fun PrimaryTransportButton(player: PlayerState, diameter: Dp = 42.dp) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered) Color.White else Color.White.copy(alpha = 0.90f),
    )

    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { player.togglePlay() },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            if (player.isPlaying) Glyph.Pause else Glyph.Play,
            Modifier.size(diameter * 0.40f),
            Color(0xFF15111F),
        )
    }
}

/**
 * Thin draggable bar used for both seeking and volume. Click-to-jump plus drag-to-scrub is
 * the pointer behaviour desktop users expect; it thickens on hover to give a bigger target.
 */
@Composable
private fun LevelBar(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val thickness by animateDpAsState(if (hovered) 6.dp else 4.dp)

    Box(
        modifier
            .height(18.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(thickness)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (hovered) 1f else 0.85f)),
        )
    }
}

@Composable
internal fun PlayerBar(player: PlayerState, modifier: Modifier = Modifier) {
    val track = player.track

    Row(
        modifier
            .padding(start = ContentStartInset, end = ChromeGap, bottom = ChromeGap)
            .fillMaxWidth()
            .height(PlayerBarHeight)
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                blurRadiusDp = 3f,
                highlightAlpha = 0.45f,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { player.isNowPlayingExpanded = true },
        ) {
            Artwork(track.artStart, track.artEnd, 54.dp, 12.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.width(176.dp)) {
            Text(
                track.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(22.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GlyphButton(Glyph.Shuffle, { player.shuffle = !player.shuffle }, active = player.shuffle)
            GlyphButton(Glyph.Previous, { player.previous() })
            PrimaryTransportButton(player)
            GlyphButton(Glyph.Next, { player.next() })
            GlyphButton(
                if (player.repeatMode == RepeatMode.One) Glyph.RepeatOne else Glyph.Repeat,
                { player.cycleRepeat() },
                active = player.repeatMode != RepeatMode.Off,
            )
        }

        Spacer(Modifier.width(22.dp))
        Text(
            formatTime(player.positionSeconds),
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(10.dp))
        LevelBar(player.progress, { player.seekTo(it) }, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            formatTime(track.durationSeconds.toFloat()),
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
        )

        Spacer(Modifier.width(20.dp))
        GlyphButton(
            if (player.isMuted || player.volume <= 0.001f) Glyph.Muted else Glyph.Volume,
            { player.isMuted = !player.isMuted },
        )
        Spacer(Modifier.width(6.dp))
        LevelBar(
            player.effectiveVolume,
            {
                player.volume = it
                player.isMuted = false
            },
            Modifier.width(88.dp),
        )
    }
}

/**
 * Expanded Now Playing panel. This is the surface [PLAYER_BLUR_MULTIPLIER] exists for: it
 * blurs far harder than the rest of the chrome so the artwork behind it reads as depth
 * rather than detail.
 */
@Composable
internal fun NowPlayingOverlay(player: PlayerState, blurRadiusDp: Float) {
    val track = player.track

    Box(
        Modifier
            .fillMaxSize()
            .padding(
                start = ContentStartInset,
                top = ContentTopInset,
                end = ChromeGap,
                bottom = ContentBottomInset,
            )
            .liquidGlass(
                shape = RoundedCornerShape(28.dp),
                blurRadiusDp = blurRadiusDp * PLAYER_BLUR_MULTIPLIER,
                highlightAlpha = 0.50f,
            )
            .padding(36.dp),
    ) {
        GlyphButton(
            Glyph.Close,
            { player.isNowPlayingExpanded = false },
            Modifier.align(Alignment.TopEnd),
            diameter = 34.dp,
        )

        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artStart, track.artEnd, 280.dp, 26.dp)
            Spacer(Modifier.width(44.dp))
            Column {
                Text(
                    "NOW PLAYING",
                    color = AccentSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    track.title,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(track.artist, color = Color.White.copy(alpha = 0.76f), fontSize = 16.sp)
                Text(track.album, color = Color.White.copy(alpha = 0.48f), fontSize = 13.sp)

                Spacer(Modifier.height(26.dp))
                Row(
                    Modifier.width(340.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatTime(player.positionSeconds),
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    LevelBar(player.progress, { player.seekTo(it) }, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatTime(track.durationSeconds.toFloat()),
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlyphButton(
                        Glyph.Shuffle,
                        { player.shuffle = !player.shuffle },
                        diameter = 38.dp,
                        active = player.shuffle,
                    )
                    GlyphButton(Glyph.Previous, { player.previous() }, diameter = 38.dp)
                    PrimaryTransportButton(player, diameter = 54.dp)
                    GlyphButton(Glyph.Next, { player.next() }, diameter = 38.dp)
                    GlyphButton(
                        if (player.repeatMode == RepeatMode.One) Glyph.RepeatOne else Glyph.Repeat,
                        { player.cycleRepeat() },
                        diameter = 38.dp,
                        active = player.repeatMode != RepeatMode.Off,
                    )
                }
            }
        }
    }
}
