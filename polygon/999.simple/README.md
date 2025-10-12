# Polygon 999.simple Android Sample

This is a minimal Android application used for quick experiments. The activity shows a piece of text and a single button. Tapping the button toggles the message.

## Project structure

- `app/` — single Android application module.
- `app/src/main/java/com/example/polygon/simple/MainActivity.kt` — activity logic.
- `app/src/main/res/layout/activity_main.xml` — layout with a `TextView` and `Button`.

## Prerequisites

- Java 17 (for example Temurin 17).
- Android SDK command-line tools with at least Build Tools 34 and a device image/emulator.
- System environment variables: `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) pointing to the SDK, and `PATH` updated to include `platform-tools` and `cmdline-tools`.
- VS Code with the Kotlin extension and Android-related helpers (e.g. Android Extension Pack).
- USB debugging enabled on the target device (or an emulator running).
- Gradle CLI (any recent release) for the initial wrapper bootstrap.

## Working in VS Code

1. Open the folder `polygon/999.simple` in VS Code.
2. Run `gradle wrapper --gradle-version 8.7` once to generate the local Gradle wrapper scripts.
3. From the VS Code terminal run `./gradlew assembleDebug` to build the APK.
4. Deploy to a connected device with `./gradlew installDebug` (requires `adb` from the Android Platform Tools, see [`howto/connect-android-device.md`](../../howto/connect-android-device.md)).
5. Launch the app manually on the device/emulator; it appears as "Polygon Simple".
6. During development use VS Code’s Java/Kotlin language features and `logcat` integration (via extensions) to inspect runtime output.

## Notes

- This folder belongs to the `polygon` scratch space and can be freely modified or discarded.
- Keep experiments self-contained inside their numbered folders so they do not impact production code.
