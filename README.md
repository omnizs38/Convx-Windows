# Convx for Windows

A desktop music player for Windows built with Kotlin and Compose Multiplatform, rendering a
full Liquid Glass material through Skia runtime shaders.

![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF)
![Compose](https://img.shields.io/badge/Compose-1.8.2-4285F4)

## What it is

Convx is a native Windows application. It packages as an MSI and an EXE via `jpackage`, runs
on a bundled JDK 21 runtime, and renders through Direct3D. The window keeps the native
Windows title bar and system menu, so resize, snap, Win+Arrow tiling and taskbar previews
all behave exactly as the OS expects - only the client area is drawn by the app.

## Liquid Glass

The glass material is the point of the project, and it is implemented in full rather than
approximated with a translucent fill:

- **Refraction** - a signed-distance field over the rounded rectangle bends the sampled
  backdrop inward along the edge, so the panel behaves like a lens rather than a blur.
- **Chromatic dispersion** - an optional seven-tap variant splits the refracted sample per
  wavelength for a prismatic edge.
- **Specular rim** - a directional highlight shader traces the corner geometry, with an
  angle that can drift on a nine second cycle.
- **Vibrancy** - a saturation colour matrix applied to the sampled backdrop.
- **Depth** - a radial term added to the refraction gradient for a domed surface.

Everything the glass samples is captured once per frame into a single backdrop layer at a
reduced resolution, then reused by every glass surface. The blur radius drives the sampling
scale, so a heavy blur costs less rather than more.

All of it is adjustable at runtime from **Glass & Playback** in the sidebar - style,
vibrancy, blur radius, surface and sheen opacity, lens height and amount, depth and
chromatic aberration.

### Architecture note

Glass surfaces are siblings of the content layer, drawn after it, never nested inside it. A
glass surface placed inside the recorded subtree would sample the snapshot it is part of.
That is why the sidebar, top bar, player bar and Now Playing panel sit above the content,
and why in-page panels use a plain translucent fill.

## Interface

The layout is the standard desktop three-region shell:

- A persistent glass **sidebar** for navigation.
- A glass **top bar** with the section title and search.
- A full-width glass **player bar** with artwork, transport, seek and volume.
- An expanded **Now Playing** panel, opened by clicking the artwork, which blurs harder than
  the rest of the chrome.

Desktop affordances throughout: hover states on every control, hand and text cursors, thin
overlay scrollbars, and a window minimum size. Transport glyphs are drawn as vectors rather
than typed as font characters, so nothing depends on a symbol font being installed and the
icons stay crisp at every display scale.

### Shortcuts

| Keys | Action |
| --- | --- |
| `Space` | Play / pause |
| Media keys | Play / pause, next, previous |
| `Esc` | Close Now Playing |

## Status

The shell, the glass pipeline and the transport UI are complete. There is no audio engine
yet - playback state runs on a simulated clock, and the library is sample data. A JVM audio
backend and an InnerTube client are the next milestones; when they land, nothing in the UI
layer needs to change.

## Building

Requires JDK 21 and Windows.

```powershell
.\gradlew run                  # run from source
.\gradlew createDistributable  # portable app folder
.\gradlew packageMsi           # MSI installer
.\gradlew packageExe           # EXE installer
```

CI builds every push on `windows-latest` and attaches the MSI, the EXE and a portable ZIP to
tagged releases.

## Licence

GPL-3.0. See [`LICENSE`](LICENSE).

The glass shader sources are vendored from [Kyant0/backdrop](https://github.com/Kyant0/backdrop)
v2.0.0, Copyright 2025 Kyant0, Apache License 2.0.

Related project: [Convx](https://github.com/omnizs38/Convx).
