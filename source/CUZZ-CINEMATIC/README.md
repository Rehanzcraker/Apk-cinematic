# CUZZ CINEMATIC

Android camera app project (Kotlin + CameraX), min Android 6.0; intended test device POCO C40 / Android 11.

## Current features
- Camera preview, rear/front camera switching, torch toggle.
- Video recording to `Movies/CUZZ CINEMATIC` in the gallery.
- Resolution selector: FHD / HD / SD (CameraX selects a supported quality with fallback).
- Requests Camera2 electronic video stabilization and optical stabilization when enabled. Actual support depends on the camera HAL; this request is not a guarantee of stabilization.
- Audio recording when microphone permission is granted.

## Build APK
1. Install Android Studio on a computer and install Android SDK Platform 35 + Build Tools 35.
2. Open this folder as an existing project.
3. Wait for Gradle sync; use JDK 17.
4. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

You can also push this project to a GitHub repository and run the included `Build CUZZ CINEMATIC APK` workflow. Download the artifact named `CUZZ-CINEMATIC-debug-apk`.

## Important limitations
- This starter version does not implement gyro-based post-processing stabilization, cinematic color grading, manual ISO/shutter/focus controls, or guaranteed 24/30/60 FPS controls. The stabilizer toggle requests Camera2 stabilization modes; many budget phone camera HALs may ignore unsupported modes.
- CameraX's quality selector chooses an available recording quality; FHD may fall back if unavailable.
- Test on the target POCO C40. Camera hardware/firmware support varies by device and ROM.
