package com.convx.windows.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * One queue entry. Artwork is a gradient pair for now - artwork loading arrives with the
 * InnerTube client, and a JVM audio backend replaces the simulated clock below.
 */
data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val artStart: Color,
    val artEnd: Color,
)

enum class RepeatMode { Off, All, One }

val DemoQueue: List<Track> = listOf(
    Track("Weightless", "Marconi Union", "Ambient Transmissions", 254, Color(0xFFE05B7A), Color(0xFF7B2BE0)),
    Track("Nightdrive", "Kavinsky", "Outrun", 218, Color(0xFF41C3F0), Color(0xFF1B4BE0)),
    Track("Solar Fields", "Hummel", "Movements", 331, Color(0xFFF5B944), Color(0xFFE0542B)),
    Track("Glass Harbour", "Ólafur Arnalds", "Tide", 287, Color(0xFF63E08A), Color(0xFF128A7B)),
    Track("Violet Static", "Tycho", "Dive", 243, Color(0xFFB98BFF), Color(0xFF3B2BE0)),
    Track("Low Orbit", "Bicep", "Isles", 302, Color(0xFFFF8FB1), Color(0xFF8A1246)),
)

/**
 * Transport state for the shell. There is no audio engine yet, so [tick] advances a
 * simulated clock; when a real backend lands it replaces the ticker and everything that
 * reads this class stays as-is.
 */
class PlayerState {
    val queue: List<Track> = DemoQueue

    var isPlaying by mutableStateOf(false)
        private set
    var index by mutableStateOf(0)
        private set

    var positionSeconds by mutableStateOf(0f)
    var volume by mutableStateOf(0.72f)
    var isMuted by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeatMode by mutableStateOf(RepeatMode.Off)
    var isNowPlayingExpanded by mutableStateOf(false)

    val track: Track get() = queue[index]

    val progress: Float
        get() = (positionSeconds / track.durationSeconds.toFloat()).coerceIn(0f, 1f)

    val effectiveVolume: Float get() = if (isMuted) 0f else volume

    fun togglePlay() {
        isPlaying = !isPlaying
    }

    fun play(position: Int) {
        index = position.mod(queue.size)
        positionSeconds = 0f
        isPlaying = true
    }

    fun next() = play(index + 1)

    /** Restarts the track first, the way every desktop player treats a second Previous press. */
    fun previous() {
        if (positionSeconds > 3f) positionSeconds = 0f else play(index - 1)
    }

    fun seekTo(fraction: Float) {
        positionSeconds = fraction.coerceIn(0f, 1f) * track.durationSeconds.toFloat()
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
    }

    fun tick(deltaSeconds: Float) {
        positionSeconds += deltaSeconds
        if (positionSeconds < track.durationSeconds) return
        when (repeatMode) {
            RepeatMode.One -> positionSeconds = 0f
            RepeatMode.Off, RepeatMode.All -> next()
        }
    }
}

fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val secondsPart = (total % 60).toString().padStart(2, '0')
    return "${total / 60}:$secondsPart"
}
