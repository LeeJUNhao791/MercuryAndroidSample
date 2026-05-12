# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

This is the **MercuryAndroidSDK** demo/sample Android application developed by FFalcon (TCL/RayNeo). It demonstrates usage of the Mercury SDK for building applications on **RayNeo X2 and X3 AR smart glasses**. The glasses pair with an Android phone and run a binocular display system (left and right lenses).

## Build & Test Commands

```bash
# Build the project
./gradlew assembleDebug

# Build release
./gradlew assembleRelease

# Run unit tests (JVM, host machine)
./gradlew test

# Run single unit test
./gradlew test --tests "com.ffalcon.mercury.android.sdk.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### SDK Dependency

The actual Mercury SDK is provided as a prebuilt `.aar` file at `app/libs/MercuryAndroidSDK-v0.2.5-*.aar`. There is also `RayNeoIPCSDK-For-Android-*.aar` for communication between phone and glasses firmware. The SDK source code is **not** in this repo — this project only contains demo activities that consume the SDK's APIs.

### Core SDK Concepts (from `com.ffalcon.mercury.android.sdk.*`)

- **`BaseMirrorActivity<T>`** — Base activity for the glasses' dual-display (binocular) paradigm. Provides `mBindingPair` which manages a left-eye and right-eye ViewBinding pair (via `ViewPair`). All demo activities extend this.
- **`BaseEventActivity`** — Simpler base activity without mirroring, for phone-only UI.
- **`TempleAction` / `TempleActionViewModel`** — The glasses' temple touchpad input system. Events include `Click`, `DoubleClick`, and swipe gestures. Activities collect from `templeActionViewModel.state` in a `repeatOnLifecycle(RESUMED)` coroutine.
- **Focus system** — `FixPosFocusTracker`, `FocusHolder`, `FocusInfo` manage which UI element has input focus on the glasses. `reqFocus()` / `releaseFocus()` / `hasFocus` control focus lifecycle. Critical for directing temple touch input.
- **`ViewPair<L, R>`** — Generic container for left/right eye view bindings. `setLeft { }` / `updateView { }` pattern sets up dual-display UIs.
- **`make3DEffectForSide(view, isLeft, hasFocus)`** — Applies a 3D visual effect to focused items on the glasses display.
- **`RecyclerViewSlidingTracker`** — Manages RecyclerView scrolling via temple swipe gestures on the glasses, with synchronized left/right displays.
- **`FLogger`** — SDK logging utility.
- **`FToast`** — SDK toast for glasses display.

### Package Structure

```
com.ffalcon.mercury.android.sdk.demo/
  ├── MercuryDemoApplication.kt    — Initializes MercurySDK
  ├── DemoHomeActivity.kt          — Main menu (extends BaseMirrorActivity)
  └── ui/
      ├── activity/
      │   ├── api/                  — GPS, BLE, IMU sensor demos
      │   ├── camera/               — Camera preview demos
      │   ├── fusion/               — Fusion vision, PAG, mirror container
      │   ├── player/               — Video playback
      │   ├── recycle/              — RecyclerView with focus tracking
      │   ├── tp/                   — Temple touchpad events
      │   ├── DialogActivity.kt
      │   └── FragmentDemoActivity.kt
      ├── adapter/                  — RecyclerView adapters
      ├── entity/                   — Data classes (Contact)
      ├── fragment/                 — Fragments for fragment demo
      └── widget/                   — Custom TitleView widget
```

### Key Patterns

- **All glasses-facing activities** extend `BaseMirrorActivity<Binding>` and manage input via `templeActionViewModel.state` collected in `repeatOnLifecycle(RESUMED)`.
- **Navigation**: `DoubleClick` on temple typically triggers `finish()` to go back. Simple `Click` navigates between activities via `startActivity()`.
- **Focus setup**: Activities call `initFocusTarget()` + `initEvent()` in `onCreate`. Focus targets are registered with `FocusInfo` objects containing event and focus-change handlers.
- **Device detection**: `DeviceUtil.isX3Device()` distinguishes between RayNeo X3 (newer) and X2 hardware for feature gating (e.g., BLE status only on X3).
- **Permissions**: Camera permission is requested at runtime before launching camera activities.

### Constraints

- **compileSdk 32, minSdk 28, targetSdk 31** — API level is locked for the RayNeo glasses platform.
- **Kotlin 1.7.0**, AGP 8.8.0, Java 8 bytecode target.
- **Landscape-only**: All activities are locked to `screenOrientation="landscape"` in the manifest.
- **No navigation library**: Activities are launched directly with `startActivity()`, not via Jetpack Navigation.