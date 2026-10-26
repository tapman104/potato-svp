# Potato Player — Agent Rules

## Core Principle
Native first. Use Android platform APIs and mpv/libmpv capabilities before writing custom Kotlin logic. Add custom code only when native behavior is unavailable, insufficient, or demonstrably wrong for the requirement.

Follow the actual repository structure. Do not assume modules, classes, or architecture components exist unless confirmed by reading the source.

---

## Architecture Layers

### Models
- Simple Kotlin types: `data class`, `enum`, `sealed` for real domain concepts.
- No I/O, no Activity/View references, no JNI calls.
- Do not create model wrappers for every engine property without a concrete need.

### ViewModel
- Expose UI state via `StateFlow`.
- One authoritative owner for: playback position, duration, play/pause status, track selection.
- Use `viewModelScope.launch` for async work.
- Never put Activity, Window, orientation APIs, Compose layout, or raw JNI in the ViewModel.
- `togglePlayPause()` and similar controls send the command only — state updates come from the MPV observer callback, not optimistic guesses.

### UI / Composables
- Render state, send user actions. Never issue mpv commands from composition body.
- Collect high-frequency state (position, duration) only in the composable that needs it.
- Do not duplicate state across screen, ViewModel, and engine.
- Keep local state only for transient UI interaction (e.g. controls visibility toggle).

### Engine / MPV Bridge
- `MpvEngine` and `MPVLib` are the sole path for JNI calls. No duplicate wrappers or parallel command paths.
- All playback properties (pause, time-pos, duration, etc.) must be observed via `MPVLib.observeProperty`. Do not poll.
- Property change events flow: native JNI → `MPVLib.eventProperty` → `EventObserver` implementations → ViewModel state update.
- Preserve native surface and engine lifetimes across file changes and Activity recreation.

### Activity
- `PlayerActivity` owns lifecycle integration, orientation, and window/system UI settings.
- ViewModel exposes orientation preferences or video metadata — it never calls `requestedOrientation` or window APIs directly.

---

## MPV-Specific Rules
- Prefer mpv properties, commands, and events over reimplementing playback behavior in Kotlin.
- `observeProperty("pause", MPV_FORMAT_FLAG)` must be registered so play/pause state is always driven by real MPV state, never guessed.
- Do not replace mpv callbacks with polling, timers, or duplicate state owners.

---

## Coroutines and Threading
- No `runBlocking` on the main thread or in constructors.
- Blocking I/O off the main thread.
- Use existing coroutine scopes. No `GlobalScope`, no unmanaged threads.
- Prefer callbacks, Flow, or mpv property observation over busy polling or arbitrary timers.

---

## Compose Performance
- High-frequency values (position tick) collected only where needed — not at screen level.
- No expensive work, file I/O, or mpv commands during composition.
- Do not recreate the native video surface on ordinary UI state updates.

---

## Code Quality
- Smallest coherent change that fixes the real problem.
- Inspect source, callers, state ownership, and lifecycle paths before changing behavior.
- Do not add modules, interfaces, use cases, or frameworks without a concrete benefit.
- No duplicate state, duplicate observers, or parallel command paths.
- Report changed files and verification results after any change.

---

## Error Handling
- Handle failures at the layer that can respond meaningfully.
- Do not silently ignore engine, file, or playback failures.
- Do not claim a build or behavior passed unless actually verified.
