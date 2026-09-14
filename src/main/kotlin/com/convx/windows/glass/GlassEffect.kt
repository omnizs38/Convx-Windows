package com.convx.windows.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which surfaces can individually opt in or out of glass, as on Android. */
enum class GlassComponent { PLAYER, MINI_PLAYER, NAV_BAR, SIDE_PANEL }

/** Same three rendering styles the Android app exposes. */
enum class GlassStyle { LIQUID, BLUR, TRANSPARENT }

/** Maximum lens refraction in dp when the 0..1 sliders sit at 1 — matches the original. */
internal const val LENS_MAX_DP = 48f
internal const val PLAYER_BLUR_MULTIPLIER = 4f
internal const val OPAQUE_GLASS_SURFACE_OPACITY = 0.98f
private const val EffectNoOpEpsilon = 0.002f
private const val MinVisibleBlurPx = 0.05f
private const val MinVisibleRimAlpha = 0.01f
private val EdgeHighlightWidth: Dp = 0.8.dp
private const val EdgeHighlightAlpha = 0.55f
private const val HighlightAngleMin = 25f
private const val HighlightAngleMax = 65f
private const val HighlightAngleFrozen = (HighlightAngleMin + HighlightAngleMax) / 2f
private const val MIN_GLASS_RESOLUTION_SCALE = 0.30f
private const val FULL_QUALITY_BLUR_DP = 8f

/** User-configurable parameters, ported field-for-field from the Android GlassEffectConfig. */
@Stable
data class GlassEffectConfig(
    val globalEnabled: Boolean = true,
    val vibrancy: Float = 1.2f,
    val blurRadius: Float = 2f,
    val lensHeight: Float = 0.4f,
    val lensAmount: Float = 0.6f,
    val chromaticAberration: Boolean = false,
    val depthEffect: Boolean = false,
    val surfaceTintColor: Color = Color.Unspecified,
    val highlightColor: Color = Color.Unspecified,
    val highlightOpacity: Float = EdgeHighlightAlpha,
    val style: GlassStyle = GlassStyle.LIQUID,
    val surfaceOpacity: Float = 0.5f,
    val textColor: Color = Color.White,
    val playerEnabled: Boolean = true,
    val miniPlayerEnabled: Boolean = true,
    val navBarEnabled: Boolean = true,
    val sidePanelEnabled: Boolean = true,
) {
    fun isEnabledFor(component: GlassComponent): Boolean =
        globalEnabled && when (component) {
            GlassComponent.PLAYER -> playerEnabled
            GlassComponent.MINI_PLAYER -> miniPlayerEnabled
            GlassComponent.NAV_BAR -> navBarEnabled
            GlassComponent.SIDE_PANEL -> sidePanelEnabled
        }

    val anyComponentEnabled: Boolean
        get() = globalEnabled &&
            (playerEnabled || miniPlayerEnabled || navBarEnabled || sidePanelEnabled)
}

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }
val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop> { error("No AppBackdrop provided") }

/** Vibrancy preference (0..2) → saturation multiplier; 1 matches backdrop's built-in vibrancy. */
fun glassSaturation(vibrancy: Float): Float = 1f + 0.5f * vibrancy.coerceIn(0f, 2f)

fun glassResolutionScale(blurRadiusDp: Float): Float {
    val t = (blurRadiusDp / FULL_QUALITY_BLUR_DP).coerceIn(0f, 1f)
    return 1f - t * (1f - MIN_GLASS_RESOLUTION_SCALE)
}

fun shouldUseTranslucentGlassFallback(
    style: GlassStyle,
    surfaceOpacity: Float = 0f,
): Boolean = style == GlassStyle.TRANSPARENT || surfaceOpacity >= OPAQUE_GLASS_SURFACE_OPACITY

/** Content colour guaranteed to read against the composited glass, as on Android. */
fun glassContentColorFor(behind: Color, tint: Color, opacity: Float): Color {
    val effective = if (tint.isSpecified) lerp(behind, tint, opacity.coerceIn(0f, 1f)) else behind
    return if (effective.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]:
 * vibrancy (saturation), blur, lens refraction with optional chromatic dispersion,
 * the specular rim, then the surface tint — the same order and the same shaders as
 * the Android app.
 */
@Composable
fun Modifier.liquidGlass(
    config: GlassEffectConfig = LocalGlassEffectConfig.current,
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    applyEdgeEffects: Boolean = true,
    blurRadiusDp: Float = config.blurRadius,
    highlightAlpha: Float = EdgeHighlightAlpha,
    backdropScale: Float = glassResolutionScale(blurRadiusDp),
): Modifier {
    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    val resolutionScale = backdropScale.coerceIn(0.05f, 1f)

    val blurPx = remember(density, blurRadiusDp, resolutionScale) {
        with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    }
    val saturation = remember(config.vibrancy) { glassSaturation(config.vibrancy) }
    val lensHeightPx = remember(density, config.lensHeight) {
        with(density) { (config.lensHeight * LENS_MAX_DP).dp.toPx() }
    }
    val lensAmountPx = remember(density, config.lensAmount) {
        with(density) { (config.lensAmount * LENS_MAX_DP).dp.toPx() }
    }
    val rimWidthPx = with(density) { EdgeHighlightWidth.toPx() }

    val surfaceTintColor = remember(config.surfaceTintColor) {
        if (config.surfaceTintColor.isSpecified) config.surfaceTintColor else Color(0xFF4A4A4E)
    }

    if (shouldUseTranslucentGlassFallback(config.style, config.surfaceOpacity)) {
        return this
            .clip(shape)
            .background(surfaceTintColor.copy(alpha = config.surfaceOpacity.coerceIn(0f, 1f)))
    }

    val plainBlur = config.style == GlassStyle.BLUR
    val applySaturation = abs(saturation - 1f) > EffectNoOpEpsilon
    val applyBlur = blurPx > MinVisibleBlurPx
    val applyLens = !plainBlur && applyEdgeEffects && (lensHeightPx > 0f || lensAmountPx > 0f)

    val rimColor = if (config.highlightColor.isSpecified) config.highlightColor else Color.White
    val rimAlpha = (highlightAlpha * (config.highlightOpacity / EdgeHighlightAlpha)).coerceIn(0f, 1f)
    val drawRim = applyEdgeEffects && !plainBlur && rimAlpha >= MinVisibleRimAlpha

    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    return this
        .clip(shape)
        .onGloballyPositioned { positionInRoot = it.positionInRoot() }
        .drawWithContent {
            val image = backdrop.image
            val w = size.width
            val h = size.height
            if (image != null && w >= 1f && h >= 1f) {
                val radii = shape.cornerRadii(size, layoutDirection, this)
                val pad = if (applyBlur) blurPadding(blurPx) else 0
                val sw = (w + 2 * pad).roundToInt()
                val sh = (h + 2 * pad).roundToInt()

                // 1. Sample the backdrop region behind this surface, applying vibrancy
                //    and blur while it is copied — the equivalent of the colorControls +
                //    blur links of the Android RenderEffect chain.
                val scale = backdrop.scale
                val srcLeft = (positionInRoot.x - backdrop.origin.x - pad) * scale
                val srcTop = (positionInRoot.y - backdrop.origin.y - pad) * scale
                val src = Rect.makeXYWH(srcLeft, srcTop, sw * scale, sh * scale)

                val staging = Surface.makeRasterN32Premul(sw, sh)
                val samplePaint = Paint().apply {
                    if (applySaturation) colorFilter = saturationColorFilter(saturation)
                    if (applyBlur) {
                        imageFilter = ImageFilter.makeBlur(blurPx, blurPx, FilterTileMode.CLAMP)
                    }
                }
                staging.canvas.drawImageRect(
                    image,
                    src,
                    Rect.makeWH(sw.toFloat(), sh.toFloat()),
                    SamplingMode.LINEAR,
                    samplePaint,
                    true,
                )
                val sampled = staging.makeImageSnapshot()
                val contentShader = sampled.makeShader(
                    FilterTileMode.CLAMP,
                    FilterTileMode.CLAMP,
                    SamplingMode.LINEAR,
                    Matrix33.makeTranslate(-pad.toFloat(), -pad.toFloat()),
                )

                val canvas = nativeCanvasOf()

                // 2. Lens refraction (with optional chromatic dispersion).
                val glassPaint = Paint()
                glassPaint.shader = if (applyLens) {
                    val effect = if (config.chromaticAberration) {
                        GlassShaders.refractionWithDispersion
                    } else {
                        GlassShaders.refraction
                    }
                    RuntimeShaderBuilder(effect).apply {
                        uniform("size", w, h)
                        uniform("offset", 0f, 0f)
                        uniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
                        uniform("refractionHeight", lensHeightPx)
                        uniform("refractionAmount", -lensAmountPx)
                        uniform("depthEffect", if (config.depthEffect) 1f else 0f)
                        if (config.chromaticAberration) uniform("chromaticAberration", 1f)
                        child("content", contentShader)
                    }.makeShader()
                } else {
                    contentShader
                }

                val rrect = RRect.makeComplexLTRB(0f, 0f, w, h, radii)
                canvas.save()
                canvas.clipRRect(rrect, true)
                canvas.drawRRect(rrect, glassPaint)

                // 3. Surface tint.
                if (config.surfaceOpacity > 0f) {
                    drawRect(color = surfaceTintColor.copy(alpha = config.surfaceOpacity), size = size)
                }

                // 4. Specular rim.
                if (drawRim) {
                    val inset = rimWidthPx / 2f
                    val rimRect = RRect.makeComplexLTRB(
                        inset, inset, w - inset, h - inset,
                        FloatArray(4) { (radii[it] - inset).coerceAtLeast(0f) },
                    )
                    val rim = Paint().apply {
                        mode = PaintMode.STROKE
                        strokeWidth = rimWidthPx
                        isAntiAlias = true
                        shader = RuntimeShaderBuilder(GlassShaders.highlight).apply {
                            uniform("size", w, h)
                            uniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
                            uniform(
                                "color",
                                rimColor.red,
                                rimColor.green,
                                rimColor.blue,
                                rimAlpha,
                            )
                            uniform("angle", (HighlightAngleFrozen * Math.PI / 180f).toFloat())
                            uniform("falloff", 2f)
                        }.makeShader()
                    }
                    canvas.drawRRect(rimRect, rim)
                }

                canvas.restore()
                sampled.close()
                staging.close()
            }
            drawContent()
        }
}

private fun saturationColorFilter(saturation: Float): ColorFilter {
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

/** Corner radii in px, clamped to half the smaller side — the lens shader needs float4. */
private fun CornerBasedShape.cornerRadii(
    size: androidx.compose.ui.geometry.Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density,
): FloatArray {
    val maxRadius = size.minDimension / 2f
    val ltr = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr
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
