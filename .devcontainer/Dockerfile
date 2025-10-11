FROM ubuntu:22.04@sha256:09506232a8004baa32c47d68f1e5c307d648fdd59f5e7eaa42aaf87914100db3

SHELL ["/bin/bash", "-lc"]

# Prevent interactive prompts during package installation.
ENV DEBIAN_FRONTEND=noninteractive

# Location of the Android SDK/NDK and commonly used environment variables.
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_NDK_ROOT=/opt/android-sdk/ndk/26.1.10909125 \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# Install system dependencies and OpenJDK (required for Gradle/Android builds).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        openssh-client \
        openjdk-17-jdk \
        python3 \
        unzip \
        zip \
        wget \
        build-essential \
    && rm -rf /var/lib/apt/lists/*

# Download and install the Android command-line tools.
ARG CMDLINE_TOOLS_VERSION=11076708
RUN set -euxo pipefail \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
        -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp \
    && mv /tmp/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -f /tmp/cmdline-tools.zip

# Ensure command-line tools are reachable from PATH.
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Install the Android SDK components, build tools, platform tools, and a recent NDK.
RUN set -eux \
    && yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses \
    && sdkmanager --sdk_root="${ANDROID_HOME}" \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "ndk;26.1.10909125"

# Create an unprivileged user that will own the workspace.
ARG USER_NAME=android
ARG USER_UID=1000
ARG USER_GID=1000
RUN groupadd --gid "${USER_GID}" "${USER_NAME}" \
    && useradd --uid "${USER_UID}" --gid "${USER_GID}" --create-home --shell /bin/bash "${USER_NAME}" \
    && mkdir -p /workspace \
    && chown -R "${USER_NAME}:${USER_NAME}" /workspace "${ANDROID_HOME}"

USER ${USER_NAME}
WORKDIR /workspace

# Default to an interactive shell so the container can be used for local development.
CMD ["/bin/bash"]
