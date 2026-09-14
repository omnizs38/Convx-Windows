# Convx for Windows

Native Windows port of [Convx](https://github.com/omnizs38/Convx) — **no Electron, no web view**.

* **Stack:** Kotlin 2.1 + Compose Multiplatform Desktop (JVM/Skia) — the same Compose UI toolkit the Android app uses, so screens port over instead of being rewritten in HTML.
* **Liquid Glass:** the real thing, not a translucent rectangle. The vendored `Kyant0/backdrop` AGSL shaders from the original (`RoundedRectRefraction`, `RoundedRectRefractionWithDispersion`, `DefaultHighlight`) are reused verbatim as SkSL and run through Skia's `RuntimeEffect` on the desktop renderer (Direct3D on Windows). Backdrop capture, saturation/vibrancy, blur, lens refraction, chromatic dispersion and the specular rim all behave as on Android.
* **Build:** everything happens in GitHub Actions on `windows-latest` — MSI installer, EXE installer and a portable ZIP. No local toolchain, no Gradle wrapper required.

## Build artifacts

Push to `main` or run the **Build Windows** workflow manually; artifacts appear on the run page.
Pushing a `v*` tag additionally publishes a GitHub Release with the MSI/EXE/ZIP attached.

## Structure

```
src/main/kotlin/com/convx/windows/
  Main.kt                 window + app entry
  ui/App.kt               demo shell: content backdrop, floating glass nav bar, glass mini player
  glass/Shaders.kt        SkSL shaders ported 1:1 from the Android app
  glass/Backdrop.kt       backdrop capture (reused Skia surface snapshot), shared with all glass surfaces
  glass/GlassEffect.kt    GlassEffectConfig + Modifier.liquidGlass()
  glass/Skia.kt           Skia helpers: blur sigma, vibrancy matrix, scratch surfaces, corner radii
```

## How a glass surface is drawn

Per frame, in order: the backdrop subtree is recorded once into an off-screen surface, then each
glass surface samples the region behind it, boosts vibrancy, blurs, refracts it through the
rounded-rect lens, tints, gleams and strokes the specular rim.

Everything up to the tint runs in *backdrop working space* (capture scale x per-surface
`backdropScale`), and the finished shader is scaled back to layout space with a local matrix, so
the costly passes never touch full-resolution pixels. The rim is drawn at full resolution to stay
crisp.

### Fixes over the first cut of the port

* **Blur strength matched to Android.** `RenderEffect.createBlurEffect` takes a *radius*, Skia's
  `makeBlur` takes a Gaussian *sigma*; passing the radius through blurred ~1.7x too hard and made
  glass read as opaque frost. Now converted (`radius * 0.57735 + 0.5`).
* **One consistent scale space.** Layout px and working px were mixed, so the lens read far too
  deep on high-DPI displays. Radii, lens depth/amount and blur sigma are all scaled together now.
* **No use-after-free.** Snapshots were closed right after being drawn, but Skia can reference an
  image until the frame is flushed — a source of black/torn glass. The last few snapshots are kept
  alive and the oldest is freed, so memory stays bounded.
* **Correct rim colour.** `layout(color)` expects the host to colour-manage the uniform, which
  Skia's `RuntimeShaderBuilder.uniform(...)` does not; the shader now takes a plain premultiplied
  `half4`.
* **Edge sampling.** The sampled region is padded by the blur kernel, so blur and lens never
  sample past the captured pixels and edges no longer smear.

### Optimizations

* Off-screen surfaces and paints are allocated once per surface and reused; resized only when the
  surface changes size.
* Runtime effects are compiled once per process.
* Layout position is kept in a plain holder read during draw — using Compose state here recomposed
  the subtree on every scrolled pixel.
* Backdrop recording is skipped entirely when nothing samples it (`GlassEffectConfig.needsBackdrop`).
* Heavier blur is sampled at lower resolution (`glassResolutionScale`), down to 30%.

### Improvements over the Android build

* The specular rim slowly drifts (~9s) instead of being frozen at 45° — the Android app froze it to
  save battery, which is not a concern on a plugged-in desktop. Disable with
  `GlassEffectConfig(animateHighlight = false)`.
* A subtle top-down inner gleam (`sheenOpacity`) adds thickness to the material; set to `0f` for
  exact Android parity.

## Porting status

| Area | State |
| --- | --- |
| Liquid Glass design system (blur, refraction, dispersion, rim, tint, per-surface config) | done |
| App shell: glass nav bar, glass buttons, mini player, scrollable content backdrop | done |
| Windows packaging via Actions (MSI / EXE / portable ZIP) | done |
| InnerTube client, playback (Media3 → needs a JVM audio backend), Room DB, lyrics, Discord RPC, Listen Together | next milestones |

Licensed GPL-3.0, like the original. Shader sources are Apache-2.0, Copyright 2025 Kyant0.
