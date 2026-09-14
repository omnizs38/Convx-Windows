package com.convx.windows.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import kotlin.math.ceil

/** Largest lens depth, in dp, that `lensHeight` interpolates towards. */
const val LENS_MAX_DP = 48f

/** The expanded Now Playing panel blurs far harder than the rest of the chrome. */
const val PLAYER_BLUR_MULTIPLIER = 4f

const val MIN_GLASS_RESOLUTION_SCALE = 0.30f
const val FULL_QUALITY_BLUR_DP = 8f

val EdgeHighlightWidth: Dp = 0.8.dp
const val EdgeHighlightAlpha = 0.55f

/** Static rim angle used when highlight drift is switched off. */
const val HighlightAngleFrozen = 45f

private const val HighlightAngleMin = 28f
private const val HighlightAngleMax = 62f
private const val HighlightDriftMillis = 9000
private const val MinVisibleAlpha = 0.01f

private val DefaultDarkTint = Color(0xFF4A4A4E)
private val DefaultLightTint = Color(0xFFFAFAFA)

enum class GlassStyle {
    /** Sampled backdrop, vibrancy, blur, lens refraction and specular rim. */
    LIQUID,

    /** Sampled backdrop and blur only - no lens, no rim. */
    BLUR,

    /** No sampling at all: a flat translucent tint. Cheapest, and the fallback. */
    TRANSPARENT,
}

@Immutable
data class GlassEffectConfig(
    val style: GlassStyle = GlassStyle.LIQUID,
    val vibrancy: Float = 1.2f,
    val blurRadius: Float = 2f,
    val lensHeight: Float = 0.4f,
    val lensAmount: Float = 0.6f,
    val depthEffect: Float = 0f,
    val chromaticAberration: Float = 0f,
    val surfaceTintColor: Color = Color.Unspecified,
    val surfaceOpacity: Float = 0.5f,
    /** Slight top-down inner gleam. */
    val sheenOpacity: Float = 0.08f,
    /** Drifts the specular rim instead of freezing it at [HighlightAngleFrozen]. */
    val animateHighlight: Boolean = true,
) {
    /** With a flat tint nothing samples pixels, so the backdrop need not be recorded. */
    val needsBackdrop: Boolean get() = style != GlassStyle.TRANSPARENT
}

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }
val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Vibrancy expressed as a saturation multiplier for the sampled backdrop. */
fun glassSaturation(vibrancy: Float): Float = 1f + 0.5f * vibrancy

/**
 * Heavier blur destroys detail, so it can be sampled at a lower resolution for free: full
 * resolution up to a light blur, then tapering to a floor.
 */
fun glassResolutionScale(blurRadiusDp: Float): Float =
    if (blurRadiusDp <= 0f) {
        1f
    } else {
        (FULL_QUALITY_BLUR_DP / (blurRadiusDp + FULL_QUALITY_BLUR_DP - 1f))
            .coerceIn(MIN_GLASS_RESOLUTION_SCALE, 1f)
    }

fun shouldUseTranslucentGlassFallback(style: GlassStyle, surfaceOpacity: Float): Boolean =
    style == GlassStyle.TRANSPARENT || surfaceOpacity >= 0.99f

/**
 * Applies the Convx liquid glass material to this surface.
 *
 * The pipeline, in order: sample the backdrop region behind the surface, boost vibrancy,
 * blur, refract through a rounded-rect lens, tint, gleam, rim.
 *
 * Everything except the tint, the gleam and the rim runs in *backdrop working space* -
 * backdrop capture scale times [backdropScale] - and the finished shader is scaled back up
 * with a local matrix. That keeps the expensive passes off full-resolution pixels, and it is
 * also why the shader uniforms (radii, lens depth, lens amount, blur sigma) are all scaled:
 * an early cut of this code mixed layout px with working px and the lens read far too deep
 * on high-DPI displays, which is exactly where Windows scaling puts most machines.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    config: GlassEffectConfig = LocalGlassEffectConfig.current,
    blurRadiusDp: Float = config.blurRadius,
    applyEdgeEffects: Boolean = true,
    highlightAlpha: Float = EdgeHighlightAlpha,
    backdropScale: Float = glassResolutionScale(blurRadiusDp),
): Modifier {
    val backdrop = LocalAppBackdrop.current
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val tint = when {
        config.surfaceTintColor.isSpecified -> config.surfaceTintColor
        isLightTheme -> DefaultLightTint
        else -> DefaultDarkTint
    }
    val opacity = config.surfaceOpacity.coerceIn(0f, 1f)

    if (backdrop == null || shouldUseTranslucentGlassFallback(config.style, opacity)) {
        return this.clip(shape).background(tint.copy(alpha = opacity))
    }

    val liquid = config.style == GlassStyle.LIQUID
    val drawRim = liquid && applyEdgeEffects && highlightAlpha > MinVisibleAlpha
    val rimAngle = rememberHighlightAngle(enabled = drawRim && config.animateHighlight)

    val scratch = remember { GlassScratch() }
    val bounds = remember { GlassBounds() }
    DisposableEffect(scratch) { onDispose { scratch.dispose() } }

    val saturation = glassSaturation(config.vibrancy)
    val saturationFilter = remember(saturation) {
        if (saturation in 0.99f..1.01f) null else saturationColorFilter(saturation)
    }
    val workingScale = backdropScale.coerceIn(0.05f, 1f)

    return this
        .clip(shape)
        // A plain holder, not state: the position is only ever read during draw, and using
        // state here recomposed the whole subtree on every scrolled pixel.
        .onPlaced { bounds.offsetInRoot = it.positionInRoot() }
        .drawWithContent {
            val image = backdrop.image
            if (image == null || size.minDimension < 1f) {
                drawRect(color = tint.copy(alpha = opacity))
                drawContent()
                return@drawWithContent
            }

            val captureScale = backdrop.scale
            val scale = (captureScale * workingScale).coerceIn(0.05f, 1f)
            val sigma = blurRadiusToSigma(blurRadiusDp.dp.toPx() * scale)
            val pad = if (sigma > 0f) ceil(sigma * BlurPaddingFactor).toInt().coerceAtLeast(1) else 1
            val padLayout = pad / scale

            val stageWidth = ceil(size.width * scale).toInt() + 2 * pad
            val stageHeight = ceil(size.height * scale).toInt() + 2 * pad
            val stage = scratch.surfaces.surfaceOf(stageWidth, stageHeight)
            val stageCanvas = stage.canvas
            stageCanvas.clear(0)

            // Copy the region behind this surface out of the shared backdrop snapshot,
            // padded so the blur and the lens never sample past the captured pixels.
            val relativeX = bounds.offsetInRoot.x - backdrop.origin.x - padLayout
            val relativeY = bounds.offsetInRoot.y - backdrop.origin.y - padLayout
            val source = Rect.makeXYWH(
                relativeX * captureScale,
                relativeY * captureScale,
                (size.width + 2 * padLayout) * captureScale,
                (size.height + 2 * padLayout) * captureScale,
            )
            val samplePaint = scratch.samplePaint.apply {
                colorFilter = saturationFilter
                imageFilter = if (sigma > 0f) {
                    ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
                } else {
                    null
                }
            }
            stageCanvas.drawImageRect(
                image,
                source,
                Rect.makeWH(stageWidth.toFloat(), stageHeight.toFloat()),
                SamplingMode.LINEAR,
                samplePaint,
                true,
            )

            val sampled = scratch.surfaces.snapshot()
            val contentShader = sampled.makeShader(
                FilterTileMode.CLAMP,
                FilterTileMode.CLAMP,
                SamplingMode.LINEAR,
                // Shader origin at the surface's top-left rather than the padded stage's.
                Matrix33.makeTranslate(-pad.toFloat(), -pad.toFloat()),
            )

            val radii = shape.cornerRadiiPx(size, layoutDirection, this)
            val canvas = drawContext.canvas.nativeCanvas
            val outline = RRect.makeComplexLTRB(0f, 0f, size.width, size.height, radii)

            val lensHeightPx = (config.lensHeight * LENS_MAX_DP).dp.toPx() * scale
            val lensAmountPx = (config.lensAmount * LENS_MAX_DP).dp.toPx() * scale
            val glassShader = if (liquid && lensHeightPx > 0.5f) {
                val dispersion = config.chromaticAberration > 0f
                val builder = RuntimeShaderBuilder(
                    if (dispersion) GlassShaders.refractionWithDispersion else GlassShaders.refraction,
                )
                builder.child("content", contentShader)
                builder.uniform("size", size.width * scale, size.height * scale)
                builder.uniform("offset", 0f, 0f)
                val scaled = radii.scaledBy(scale)
                builder.uniform("cornerRadii", scaled[0], scaled[1], scaled[2], scaled[3])
                builder.uniform("refractionHeight", lensHeightPx)
                builder.uniform("refractionAmount", -lensAmountPx)
                builder.uniform("depthEffect", config.depthEffect)
                if (dispersion) builder.uniform("chromaticAberration", config.chromaticAberration)
                // Working space back to layout space.
                builder.makeShader(Matrix33.makeScale(1f / scale))
            } else {
                // Rebuild the shader from the sampled image with the working-to-layout scale
                // folded into the pad translate. Skiko has neither Shader.makeWithLocalMatrix
                // nor Matrix33.makeConcat here, so write the row-major matrix out directly:
                // scale(1/scale) * translate(-pad, -pad).
                val inv = 1f / scale
                val shift = -pad.toFloat() * inv
                sampled.makeShader(
                    FilterTileMode.CLAMP,
                    FilterTileMode.CLAMP,
                    SamplingMode.LINEAR,
                    Matrix33(
                        inv, 0f, shift,
                        0f, inv, shift,
                        0f, 0f, 1f,
                    ),
                )
            }

            canvas.save()
            canvas.clipRRect(outline, true)
            canvas.drawRect(
                Rect.makeWH(size.width, size.height),
                scratch.glassPaint.apply {
                    shader = glassShader
                    isAntiAlias = true
                },
            )
            canvas.restore()

            drawRect(color = tint.copy(alpha = opacity))

            if (liquid && config.sheenOpacity > MinVisibleAlpha) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = config.sheenOpacity),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = config.sheenOpacity * 0.5f),
                    ),
                )
            }

            if (drawRim) {
                val strokeWidth = EdgeHighlightWidth.toPx()
                val inset = strokeWidth / 2f
                val rimRadii = FloatArray(4) { (radii[it] - inset).coerceAtLeast(0f) }
                val alpha = highlightAlpha.coerceIn(0f, 1f)
                val builder = RuntimeShaderBuilder(GlassShaders.highlight)
                builder.uniform("size", size.width - strokeWidth, size.height - strokeWidth)
                builder.uniform("cornerRadii", rimRadii[0], rimRadii[1], rimRadii[2], rimRadii[3])
                // Premultiplied, as SkSL shaders must return.
                builder.uniform("color", alpha, alpha, alpha, alpha)
                builder.uniform("angle", Math.toRadians(rimAngle.value.toDouble()).toFloat())
                builder.uniform("falloff", 2f)
                canvas.save()
                canvas.translate(inset, inset)
                canvas.drawRRect(
                    RRect.makeComplexLTRB(
                        0f,
                        0f,
                        size.width - strokeWidth,
                        size.height - strokeWidth,
                        rimRadii,
                    ),
                    scratch.rimPaint.apply {
                        shader = builder.makeShader()
                        this.strokeWidth = strokeWidth
                    },
                )
                canvas.restore()
            }

            drawContent()
        }
}

/** Mutable, non-state layout position holder - see the `onPlaced` note above. */
internal class GlassBounds {
    var offsetInRoot: Offset = Offset.Zero
}

/**
 * Drifts the specular rim angle on a slow cycle. A mains-powered desktop can afford to let
 * the light move, and that movement is what sells the material as glass rather than a decal.
 * The value is read inside the draw lambda only, so it invalidates draw without recomposing
 * anything. Switch it off with `GlassEffectConfig.animateHighlight` to pin the angle.
 */
@Composable
private fun rememberHighlightAngle(enabled: Boolean): State<Float> {
    if (!enabled) return remember { mutableStateOf(HighlightAngleFrozen) }
    val transition = rememberInfiniteTransition(label = "glassHighlight")
    return transition.animateFloat(
        initialValue = HighlightAngleMin,
        targetValue = HighlightAngleMax,
        animationSpec = infiniteRepeatable(
            animation = tween(HighlightDriftMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glassHighlightAngle",
    )
}
