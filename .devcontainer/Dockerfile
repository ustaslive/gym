FROM ubuntu:22.04@sha256:09506232a8004baa32c47d68f1e5c307d648fdd59f5e7eaa42aaf87914100db3

SHELL ["/bin/bash", "-lc"]

# Prevent interactive prompts during package installation.
ENV DEBIAN_FRONTEND=noninteractive

# Location of the Android SDK/NDK and commonly used environment variables.
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_NDK_ROOT=/opt/android-sdk/ndk/26.1.10909125 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# Install system dependencies and OpenJDK (required for Gradle/Android builds).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        less \
        openssh-client \
        openjdk-17-jdk \
        python3 \
        unzip \
        zip \
        wget \
        build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle for project builds.
ARG GRADLE_VERSION=8.7
RUN set -euxo pipefail \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
        -o /tmp/gradle.zip \
    && mkdir -p /opt/gradle \
    && unzip -q /tmp/gradle.zip -d /opt/gradle \
    && rm -f /opt/gradle/latest \
    && ln -s "/opt/gradle/gradle-${GRADLE_VERSION}" /opt/gradle/latest \
    && rm -f /tmp/gradle.zip

# Download and install the Android command-line tools.
ARG CMDLINE_TOOLS_VERSION=11076708
RUN set -euxo pipefail \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
        -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp \
    && mv /tmp/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -f /tmp/cmdline-tools.zip

# Ensure build toolchains are reachable from PATH.
ENV GRADLE_HOME=/opt/gradle/latest
ENV PATH="${GRADLE_HOME}/bin:${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

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
    && chown -R "${USER_NAME}:${USER_NAME}" /workspace "${ANDROID_HOME}" \
    && install -d -o "${USER_NAME}" -g "${USER_NAME}" "/home/${USER_NAME}/.gradle" \
    && install -d -o "${USER_NAME}" -g "${USER_NAME}" "/home/${USER_NAME}/.android" \
    && install -d -o "${USER_NAME}" -g "${USER_NAME}" "/home/${USER_NAME}/.cache"

USER ${USER_NAME}
WORKDIR /workspace

# Default to an interactive shell so the container can be used for local development.
CMD ["/bin/bash"]
