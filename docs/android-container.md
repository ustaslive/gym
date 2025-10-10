# Android Development Container

This container lives in `.devcontainer` and provides a reproducible Android build environment that keeps the host machine clean. It bundles the command-line SDK tools, platform-tools (including `adb`), build-tools 34.0.0, and NDK 26.1.10909125 on top of Ubuntu 22.04 with OpenJDK 17.

## Build the image

```sh
docker build -f .devcontainer/Dockerfile -t gym-android-dev .
```

The build downloads the Android command-line tools and installs the SDK components listed in the `Dockerfile`. You can adjust versions through the build arguments:

```sh
docker build \
  -f .devcontainer/Dockerfile \
  --build-arg CMDLINE_TOOLS_VERSION=11076708 \
  --build-arg USER_UID=$(id -u) \
  --build-arg USER_GID=$(id -g) \
  -t gym-android-dev .
```

## Start a development shell

Mount the repository into `/workspace` so the container works directly with the local sources:

```sh
docker run --rm -it \
  --network=host \
  -v "$(pwd)":/workspace \
  -w /workspace \
  gym-android-dev
```

The default user inside the container matches the UID/GID passed at build time (defaults to 1000). This prevents permission issues on generated build artifacts.

## Using ADB over Wi-Fi

1. Enable developer options and wireless debugging on the Android device.
2. Pair the device with the host once via USB and enable TCP/IP mode:
   ```sh
   adb tcpip 5555
   ```
3. Disconnect USB, ensure the device shares the same network as your machine, then connect from inside the container:
   ```sh
   adb connect <device-ip>:5555
   ```
4. Verify the device is visible:
   ```sh
   adb devices
   ```

When the container starts, it has the SDK tools, `adb`, and `gradle` prerequisites ready for building, testing, and deploying directly to the paired device. The same configuration is used by the VS Code Dev Container defined in `.devcontainer/devcontainer.json`.

## Customizing the SDK

If you need additional API levels or build tools, open a shell inside the container and install them with `sdkmanager`. For example:

```sh
sdkmanager "platforms;android-33" "system-images;android-33;google_apis;x86_64"
yes | sdkmanager --licenses
```

Add persistent requirements directly to the `Dockerfile` so future builds stay consistent.
