# Connect an Android Device to the Dev Environment

These steps let you deploy `./gradlew installDebug` builds to a real phone using the tooling that ships with this repo's devcontainer.

## 1. Check prerequisites
- The devcontainer must be running so that `adb` is available inside the shell (`adb version`).
- Your phone should have at least Android 5.0 for USB debugging, or Android 11.0+ if you want wireless debugging.
- Use the same USB cable you would use for data transfer (not charge-only).

## 2. Enable developer options on the phone
1. Open **Settings → About phone**.
2. Tap **Build number** seven times until it says you are a developer.
3. Go back to **Settings → System → Developer options** (location can vary by vendor).
4. Enable **USB debugging**. If you plan to debug wirelessly on Android 11+, also enable **Wireless debugging**.

## 3. First-time USB authorization
1. Connect the phone to your machine with USB.
2. On the phone, confirm the **Allow USB debugging** prompt (select *Always allow* if this is a trusted machine).
3. In the container shell run:
   ```bash
   adb devices
   ```
   You should see the device listed as `device`. If it shows `unauthorized`, re-check the phone prompt.

You can now deploy with:
```bash
./gradlew installDebug
```

## 4. Optional: switch to wireless debugging (Android 11+)
1. Make sure the phone and the devcontainer host are on the same Wi-Fi network.
2. On the phone, open **Developer options → Wireless debugging** and choose **Pair device with pairing code**.
3. Note the **IP address:port** pair and the **pairing code** shown on the phone.
4. In the container shell run:
   ```bash
   adb pair <ip-address>:<pairing-port> <pairing-code>
   ```
5. Still in the wireless debugging menu, choose **Wireless debugging → Pair using Wi-Fi** (or **Connect to device** on some devices) to get the **IP address:connection port**.
6. Connect ADB to the device:
   ```bash
   adb connect <ip-address>:<connection-port>
   ```
7. Verify with `adb devices`; the device should now appear with `device` status and stay connected over Wi-Fi.
8. You can disconnect the USB cable—Gradle deployments will now target the Wi-Fi connection.

## 5. Legacy wireless debugging (pre-Android 11)
If your device runs Android 10 or lower, you can still debug wirelessly after an initial USB connection:
```bash
adb tcpip 5555
adb connect <device-ip>:5555
```
You will need to repeat these steps whenever the device reboots.

## 6. Deploy and iterate
- Build and install with `./gradlew installDebug`.
- Use `adb logcat` to stream logs during debugging.
- When finished, disconnect with `adb disconnect`.
