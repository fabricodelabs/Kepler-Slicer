# Performance Report

Environment: Pixel_Tablet AVD (API 36, x86_64 host image; the arm64 engine runs through native-bridge translation,
so native timings are slower than on a real arm64 tablet). Debug build. Measurements are indicative.

## Startup
| Measurement | Value |
|---|---|
| Cold start, `am start -W` TotalTime (3 runs) | 589, 390, 402 ms |
| Hot start | 288 ms |

## Memory (after loading a 3MF, slicing and opening the layer preview)
| Metric (`dumpsys meminfo`) | Value |
|---|---|
| TOTAL PSS | 417.7 MB |
| Native heap | 221.0 MB (engine state, toolpaths) |
| Java heap | 48.5 MB |
| RSS | 553.6 MB |

## Rendering
`dumpsys gfxinfo` counts only View frames; the plate and toolpaths are drawn in a `GLSurfaceView` and are not
included, so jank of the 3D view was not measured (needs Perfetto/AGI on a device). UI frames sampled: median 18 ms
on the emulator.

## Audit-related changes
| Change | Cost |
|---|---|
| ArchiveGuard (inflate each 3MF entry once before import) | linear in archive size; a 350 KB project adds a few ms; a bomb is stopped after 16 MB of inflation |
| jni_safe_utf8 | one pass over each returned string; negligible next to the engine work |
| expat 2.10 vs 2.2 | same API; adds amplification accounting; no measurable change in import time |
| OBN session removal | two `File.delete` calls per cloud connect |

## Findings
| ID | Severity | Finding | Status |
|---|---|---|---|
| PERF-01 | Low | `MainActivity.Icon.onDraw` allocates `Path`/`RectF` per draw (Lint DrawAllocation ×5); small, rarely redrawn icons | Not fixed (negligible) |
| PERF-02 | Low | `UsableSpace` checks before slicing/import use `getUsableSpace` instead of `StorageManager.getAllocatableBytes` (Lint) | Not fixed |
| PERF-03 | Info | Native heap ~220 MB after slicing; the engine keeps state between runs | Not changed; monitor on low-RAM devices |
| PERF-04 | Info | Global `engine_mutex` serialises slicing and model inspection | By design |

Not measured: CPU/GPU load profiles, allocation counts (Android Studio Profiler, LeakCanary and Perfetto were not
available in this session), low-end device behaviour.
