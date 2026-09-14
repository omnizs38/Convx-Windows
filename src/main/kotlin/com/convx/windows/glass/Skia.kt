package com.convx.windows.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Surface

/**
 * Blur strength is configured as a *radius* in dp, but Skia's `ImageFilter.makeBlur` takes a
 * Gaussian *sigma*. Feeding the radius straight in (the first cut of this code did) blurs
 * roughly 1.7x too hard, so glass pills read as opaque frost instead of the near-clear
 * material intended at the 2dp default.
 */
internal fun blurRadiusToSigma(radius: Float): Float =
    if (radius <= 0f) 0f else radius * 0.57735f + 0.5f

/** Radius multiplier that covers a Gaussian kernel, used to pad the sampled region. */
internal const val BlurPaddingFactor = 3f

/**
 * Vibrancy, as a saturation colour matrix over the sampled backdrop. Skia's ColorMatrix uses
 * normalised translation (0..1), and only the 3x3 saturation part is needed here -
 * brightness and contrast are not used by any Convx surface.
 */
internal fun saturationColorFilter(saturation: Float): ColorFilter {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat
    return ColorFilter.makeMatrix(
        ColorMatrix(
            r + saturation, g, b, 0f, 0f,
            r, g + saturation, b, 0f, 0f,
            r, g, b + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/**
 * A reused off-screen Skia surface plus bounded snapshot recycling.
 *
 * Two bugs this exists to prevent:
 *  * allocating a fresh raster surface every frame per glass surface (the first cut did, and
 *    it dominated the frame at 60fps);
 *  * closing a snapshot right after drawing it. Skia draw calls can reference an image until
 *    the frame is flushed, so an immediate `close()` is a use-after-free that shows up as
 *    black or torn glass. Instead the last few snapshots stay alive and the oldest is freed,
 *    which keeps memory bounded without ever freeing an in-flight image.
 */
internal class ScratchSurface {
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private val retained = ArrayDeque<Image>()

    fun surfaceOf(width: Int, height: Int): Surface {
        val existing = surface
        if (existing == null || this.width != width || this.height != height) {
            existing?.close()
            surface = Surface.makeRasterN32Premul(width, height)
            this.width = width
            this.height = height
        }
        return surface!!
    }

    fun snapshot(): Image {
        val image = surface!!.makeImageSnapshot()
        retained.addLast(image)
        while (retained.size > RetainedFrames) retained.removeFirst().close()
        return image
    }

    fun dispose() {
        retained.forEach { it.close() }
        retained.clear()
        surface?.close()
        surface = null
        width = 0
        height = 0
    }

    private companion object {
        const val RetainedFrames = 3
    }
}

/** Per-surface scratch state: one surface plus reused paints, so drawing allocates nothing. */
internal class GlassScratch {
    val surfaces = ScratchSurface()
    val samplePaint = Paint()
    val glassPaint = Paint()
    val rimPaint = Paint().apply {
        mode = PaintMode.STROKE
        isAntiAlias = true
    }

    fun dispose() {
        surfaces.dispose()
        samplePaint.close()
        glassPaint.close()
        rimPaint.close()
    }
}

/**
 * Corner radii in px, clamped to half the shorter side. The lens and highlight shaders take a
 * `float4` of radii, with start/end corners resolved against the layout direction.
 */
internal fun CornerBasedShape.cornerRadiiPx(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): FloatArray {
    val maxRadius = size.minDimension / 2f
    val ltr = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (ltr) topStart.toPx(size, density) else topEnd.toPx(size, density)
    val topRight = if (ltr) topEnd.toPx(size, density) else topStart.toPx(size, density)
    val bottomRight = if (ltr) bottomEnd.toPx(size, density) else bottomStart.toPx(size, density)
    val bottomLeft = if (ltr) bottomStart.toPx(size, density) else bottomEnd.toPx(size, density)
    return floatArrayOf(
        topLeft.coerceAtMost(maxRadius),
        topRight.coerceAtMost(maxRadius),
        bottomRight.coerceAtMost(maxRadius),
        bottomLeft.coerceAtMost(maxRadius),
    )
}

/** Radii scaled into the backdrop working space the lens shader runs in. */
internal fun FloatArray.scaledBy(scale: Float): FloatArray = FloatArray(size) { this[it] * scale }
