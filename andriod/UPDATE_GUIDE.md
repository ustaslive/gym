# Code Update Checklist

1. Pull the latest changes from the main branch and resolve any merge conflicts locally.
2. Open the project in Android Studio (or run `./gradlew tasks`) to let Gradle download new dependencies.
3. Review `app/build.gradle.kts` and `gradle.properties` for version changes you might need to apply to your local environment.
4. Build the debug variant with `./gradlew assembleDebug` to verify the project compiles.
5. Install the debug build on a connected device if needed: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
6. Run smoke tests manually on the device to confirm that critical screens still work after the update.

### When Multiple Devices Are Connected
- Run `adb devices` to list active device IDs (for example, `199.99.9.254:43059` and `adb-H6BEXGT8E6IN4PJJ-37FM5r._adb-tls-connect._tcp`).
- Repeat the uninstall/install command with the `-s` flag pointing to the correct device ID, e.g. `adb -s adb-H6BEXGT8E6IN4PJJ-37FM5r._adb-tls-connect._tcp uninstall com.example.gymprogress`.

### If Wireless Install Is Blocked (Xiaomi)
- Open `Settings > Additional settings > Developer options`.
- Make sure `USB debugging` is enabled. Toggle `Wireless debugging`, tap it, choose `Forget all devices`, and pair again using `Pair device with pairing code`.
- Enable `USB debugging (Security settings)` or `Install via USB`, sign in to the Mi Account if prompted, and confirm the warning dialog.
- Re-run `adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk` and accept the install prompt when it appears on the phone.
