# Potato Player — Claude Context

## What This App Is
Android local video player. Kotlin + Jetpack Compose (Material 3) + mpv/libmpv via JNI.
No ExoPlayer. No XML layouts. No Hilt. No navigation framework.

---

## Stack
| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Video engine | mpv/libmpv via JNI (`MPVLib.kt`) |
| State | `StateFlow` in `PlayerViewModel` |
| Async | Kotlin coroutines (`viewModelScope`) |
| DI | None / manual |
| Persistence | Not yet implemented |

---

## Key Files
| File | Role |
|---|---|
| `PlayerActivity.kt` | Entry point, lifecycle, sets Compose content |
| `PlayScreen.kt` | Root composable — `Box` stacking video + bars |
| `PlayerViewModel.kt` | All playback state + MPV command dispatch |
| `MpvEngine.kt` | MPV init, surface attach, property observation registration |
| `MPVLib.kt` | JNI bridge — all native calls go here |
| `PlayerTopBar.kt` | Top overlay bar composable |
| `PlayerBottomBar.kt` | Bottom overlay bar composable (play/pause + seek) |
| `VideoSurface.kt` | Embeds `MpvSurfaceView` into Compose via `AndroidView` |
| `MpvSurfaceView.kt` | `SurfaceView` subclass — the only non-Compose View |
| `BaseMPVView.kt` | Base for surface lifecycle |

---

## State Flow (how MPV events reach UI)
```
Native libmpv
  → JNI callback
  → MPVLib.eventProperty(property, value)
  → MPVLib dispatches to observers list
  → PlayerViewModel.eventProperty() (registered in init, removed in onCleared)
  → _uiState.update { ... }
  → PlayScreen collects state
  → Composables recompose
```

---

## Known Architecture Decisions
- **Pause state**: driven entirely by `observeProperty("pause", MPV_FORMAT_FLAG)` callback. `togglePlayPause()` sends the command only — no optimistic state update.
- **Controls visibility**: transient UI state, lives in `PlayScreen` (not ViewModel). Single tap toggles. Auto-hides after 5 seconds via coroutine in `LaunchedEffect`.
- **Play/pause button**: centered in bottom bar. State source is `state.isPlaying` from ViewModel, which reflects real MPV pause property.
- **No polling**: all property updates via `observeProperty`. No timers or repeated reads for playback state.

---

## What Does NOT Exist (do not assume)
- No Hilt or any DI framework
- No navigation graph or NavController
- No XML layouts
- No repository or data layer
- No use case classes
- No ExoPlayer or MediaSession
- No existing gesture handler (being built fresh)
- No auto-hide timer (being built fresh)

---

## Audit Before Every Change
Before touching any file:
1. Read the file
2. Identify all callers/observers of what you're changing
3. Make the smallest change that solves the problem
4. Report what changed and confirm the build passes
