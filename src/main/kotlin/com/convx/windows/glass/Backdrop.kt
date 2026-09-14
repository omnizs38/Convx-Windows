package com.convx.windows.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Android app captures real pixels with a `rememberLayerBackdrop()` attached via
 * `Modifier.layerBackdrop(...)`, and any glass surface holding that same reference
 * samples, blurs and refracts them. This is the desktop equivalent: the backdrop
 * subtree is recorded into an off-screen Skia surface each frame and the snapshot is
 * shared with every glass surface, so the semantics of the original are preserved.
 */
class LayerBackdrop internal constructor() {
    internal var image: Image? = null
        private set

    /** Fraction of layout pixels the snapshot is recorded at. */
    internal var scale: Float = 1f
        private set

    /** Root-space origin of the recorded subtree, so surfaces can map their own bounds into it. */
    internal var origin: Offset = Offset.Zero

    internal var version by mutableStateOf(0)
        private set

    internal fun publish(image: Image, scale: Float) {
        this.image?.close()
        this.image = image
        this.scale = scale
        version++
    }
}

@Composable
fun rememberLayerBackdrop(): LayerBackdrop = remember { LayerBackdrop() }

/**
 * Records this subtree into [backdrop] and then draws it normally.
 *
 * [captureScale] mirrors the Android port's backdrop resolution scale: blur hides the
 * upscale, so the capture is cheaper than the surface it feeds.
 */
fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    captureScale: Float = 0.75f,
): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        val scale = captureScale.coerceIn(0.1f, 1f)
        val w = (size.width * scale).roundToInt()
        val h = (size.height * scale).roundToInt()
        if (w > 0 && h > 0) {
            val surface = Surface.makeRasterN32Premul(w, h)
            val canvas = surface.canvas
            canvas.scale(scale, scale)
            recordInto(canvas.asComposeCanvas())
            backdrop.publish(surface.makeImageSnapshot(), scale)
            surface.close()
        }
        drawContent()
    }

private fun ContentDrawScope.recordInto(canvas: androidx.compose.ui.graphics.Canvas) {
    CanvasDrawScope().draw(this, layoutDirection, canvas, size) {
        this@recordInto.drawContent()
    }
}

/** Padding, in backdrop pixels, needed around a surface so a blur has real samples to read. */
internal fun blurPadding(blurPx: Float): Int = max(1, (blurPx * 3f).roundToInt())

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.nativeCanvasOf() =
    drawContext.canvas.nativeCanvas
