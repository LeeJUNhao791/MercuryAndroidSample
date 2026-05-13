# Mercury SDK Android Sample Project

Project guidelines and architecture overview for the **MercuryAndroidSDK** demo application, developed by FFalcon (TCL/RayNeo) for **RayNeo X2/X3 AR smart glasses**.

## Project Overview

This is an Android sample application demonstrating the usage of the Mercury SDK. It focuses on the binocular display system and the temple touchpad interaction paradigm required for AR glasses.

- **Primary Platform:** RayNeo X2 / X3 AR Glasses
- **Main Technologies:** Kotlin, Android SDK, Mercury SDK (AAR), JNI (WeNet), PyTorch Mobile.
- **Architecture:** Binocular Mirroring (Left/Right lenses) using `BaseMirrorActivity`.

## Build & Environment

- **Gradle Wrapper:** `./gradlew` (Gradle 8.10.2)
- **NDK Version:** `21.1.6352462` (Required for WeNet native compatibility)
- **Java Home:** Java 17+ (Android Studio JBR recommended)
- **Key Commands:**
  - `assembleDebug`: Build debug APK.
  - `clean`: Remove build artifacts.
  - `extractAARForNativeBuild`: Custom task to extract native headers/libs from AARs.

## Core Development Patterns

### 1. Binocular Display (`BaseMirrorActivity`)
All glasses-facing activities must inherit from `BaseMirrorActivity<T>`.
- Use `mBindingPair.updateView { ... }` to update UI elements on both lenses simultaneously.
- Define layout files with the `layout_` prefix (e.g., `layout_wenet.xml`) to align with ViewBinding conventions.

### 2. Interaction & Focus (`FixPosFocusTracker`)
Interaction is handled via the glasses' temple touchpad.
- **Navigation:** Swipe to move focus between UI elements.
- **Action:** Single click on the temple to trigger a focused element.
- **Exit:** Double click on the temple typically calls `finish()`.
- **Implementation:**
  - Call `initFocusTarget()` and `initEvent()` in `onCreate`.
  - Register views with `FocusHolder` and `FocusInfo`.
  - Use `triggerFocus(hasFocus, view, isLeft)` to provide visual feedback (e.g., changing background color to `R.color.purple_200`).

### 3. Audio & Voice (`VOICE_RECOGNITION`)
For voice-related features (like WeNet or Sherpa), use the SDK's specialized recording modes.
- **Mode:** `AudioRecordingModeConfig.RecordingMode.VOICE_RECOGNITION`.
- **Benefit:** Activates the glasses' triple-microphone array and beamforming algorithms to capture only the wearer's voice.
- **Lifecycle:** Always call `AudioRecordingModeConfig.release(...)` in `stopRecording` and `onDestroy` to restore system audio routing.

### 4. Native Integration (WeNet)
The project includes a migrated WeNet speech recognition engine.
- **Package:** `com.mobvoi.wenet`.
- **Native Logic:** Located in `app/src/main/cpp`.
- **Models:** Large model files (e.g., `final.zip`, `units.txt`) must be manually placed in `app/src/main/assets`.

## Coding Standards

- **Language:** Strictly Kotlin for all new features.
- **Comments:** **MUST PRESERVE** existing Chinese comments. Add new comments in the same style/language as surrounding code.
- **Naming:** Follow standard Android/Kotlin naming conventions (CamelCase for classes, camelCase for variables/functions).
- **Resources:** Use `R.color.purple_200` for focused items and `R.color.black` for default backgrounds in glasses-facing UI.

## Troubleshooting

- **Crash on Start:** Ensure `WenetActivity` or other new activities inherit from `BaseMirrorActivity`.
- **Missing Symbols:** Verify `R` class imports if the activity is in a sub-package (e.g., `com.mobvoi.wenet`).
- **Native Build Errors:** Ensure NDK `21.1.6352462` is installed and the `build_DIR` in `CMakeLists.txt` correctly points to the `app/build` directory.
