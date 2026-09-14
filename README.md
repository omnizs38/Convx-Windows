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
  glass/Backdrop.kt       backdrop capture (Skia surface snapshot) + blur/vibrancy processing
  glass/GlassEffect.kt    GlassEffectConfig + Modifier.liquidGlass()
```

## Porting status

| Area | State |
| --- | --- |
| Liquid Glass design system (blur, refraction, dispersion, rim, shadow, tint, per-component config) | done |
| App shell: glass nav bar, glass buttons, mini player, scrollable content backdrop | done |
| Windows packaging via Actions (MSI / EXE / portable ZIP) | done |
| InnerTube client, playback (Media3 → needs a JVM audio backend), Room DB, lyrics, Discord RPC, Listen Together | next milestones |

Licensed GPL-3.0, like the original. Shader sources are Apache-2.0, Copyright 2025 Kyant0.
