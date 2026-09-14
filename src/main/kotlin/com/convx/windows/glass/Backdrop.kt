package com.convx.windows.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import org.jetbrains.skia.Image
import kotlin.math.ceil

/** Default fraction of layout pixels the backdrop snapshot is recorded at. */
const val DEFAULT_BACKDROP_CAPTURE_SCALE = 0.7f

internal val AlwaysCapture: () -> Boolean = { true }

/**
 * The pixels every glass surface samples, blurs and refracts.
 *
 * The subtree marked with [Modifier.layerBackdrop] is recorded once per frame into a reused
 * off-screen Skia surface, and the resulting snapshot is shared by every glass surface
 * holding this reference - so N glass panels cost one capture, not N.
 *
 * Deliberately *not* Compose state: the snapshot is written during the draw phase, and
 * writing state there would either be dropped or spin an invalidation loop. Backdrop content
 * is drawn before the glass chrome that samples it (glass surfaces are later siblings), so a
 * plain field is read back in the same frame it was written.
 */
class LayerBackdrop internal constructor() {
    internal var image: Image? = null
        private set

    /** Fraction of layout pixels the snapshot is recorded at. */
    internal var scale: Float = 1f
        private set

    /** Root-space origin of the recorded subtree, so surfaces can map their bounds into it. */
    internal var origin: Offset = Offset.Zero

    internal fun publish(image: Image, scale: Float) {
        this.image = image
        this.scale = scale
    }

    internal fun clear() {
        image = null
    }
}

@Composable
fun rememberLayerBackdrop(): LayerBackdrop = remember { LayerBackdrop() }

/**
 * Records this subtree into [backdrop], then draws it normally.
 *
 * [captureScale] records at a fraction of layout resolution: the blur hides the upscale, so
 * the capture is cheaper than the surfaces it feeds. [enabled] lets the caller skip recording
 * entirely - with the transparent glass style nothing samples the backdrop, and recording it
 * is pure cost (see `GlassEffectConfig.needsBackdrop`).
 */
@Composable
fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    captureScale: Float = DEFAULT_BACKDROP_CAPTURE_SCALE,
    enabled: () -> Boolean = AlwaysCapture,
): Modifier {
    val scratch = remember { ScratchSurface() }
    val recorder = remember { CanvasDrawScope() }
    DisposableEffect(scratch) {
        onDispose {
            backdrop.clear()
            scratch.dispose()
        }
    }

    return this
        .onPlaced { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            if (!enabled()) {
                backdrop.clear()
                drawContent()
                return@drawWithContent
            }
            val scale = captureScale.coerceIn(0.1f, 1f)
            val width = ceil(size.width * scale).toInt()
            val height = ceil(size.height * scale).toInt()
            if (width > 0 && height > 0) {
                val surface = scratch.surfaceOf(width, height)
                val canvas = surface.canvas
                canvas.clear(0)
                canvas.save()
                canvas.scale(scale, scale)
                recorder.draw(this, layoutDirection, canvas.asComposeCanvas(), size) {
                    this@drawWithContent.drawContent()
                }
                canvas.restore()
                backdrop.publish(scratch.snapshot(), scale)
            }
            drawContent()
        }
}
