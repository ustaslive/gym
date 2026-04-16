-include .env

GRADLE := ./gradlew
ADB := adb
ADB_SERIAL ?=
APPLICATION_ID := com.example.gymprogress
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

define RESOLVE_ADB_SERIAL_SH
devices="$$( $(ADB) devices | awk 'NR==1 {next} $$NF=="device" {sub(/[[:space:]]+device$$/, "", $$0); print $$0}' )"; \
devices="$$(printf '%s\n' "$$devices" | sed '/^$$/d')"; \
device_count="$$(printf '%s\n' "$$devices" | awk 'NF {count++} END {print count+0}')"; \
if [ "$$device_count" -eq 0 ]; then \
	echo "No ADB device detected."; \
	exit 1; \
fi; \
serial=""; \
if [ -n "$(ADB_SERIAL)" ]; then \
	serial="$(ADB_SERIAL)"; \
	if ! printf '%s\n' "$$devices" | grep -Fx -- "$$serial" >/dev/null; then \
		echo "ADB_SERIAL '$$serial' is not connected."; \
		echo "Connected devices:"; \
		printf '%s\n' "$$devices" | sed 's/^/  - /'; \
		exit 1; \
	fi; \
elif [ -n "$(ADB_HOST)" ]; then \
	host_matches="$$(printf '%s\n' "$$devices" | awk 'index($$0, "$(ADB_HOST):")==1 {print $$0}')"; \
	host_count="$$(printf '%s\n' "$$host_matches" | awk 'NF {count++} END {print count+0}')"; \
	if [ "$$host_count" -eq 1 ]; then \
		serial="$$host_matches"; \
	fi; \
fi; \
if [ -z "$$serial" ]; then \
	if [ "$$device_count" -eq 1 ]; then \
		serial="$$devices"; \
	else \
		echo "Multiple ADB devices detected."; \
		echo "Set ADB_SERIAL to one of:"; \
		printf '%s\n' "$$devices" | sed 's/^/  - /'; \
		exit 1; \
	fi; \
fi
endef

.PHONY: help check build install uninstall list connect

help:
	@printf "%s\n" \
		"Available commands:" \
		"  make help     Show this help message." \
		"  make list     List ADB devices." \
		"  make check    Verify that a target ADB device can be resolved." \
		"  make connect  Connect to an ADB host using ADB_HOST and a prompted port." \
		"  make build    Build the debug APK with Gradle." \
		"  make install  Build the debug APK if needed and install it on the selected device." \
		"  make uninstall Uninstall the app from the selected device." \
		"" \
		"Examples:" \
		"  make help" \
		"  make list" \
		"  make check" \
		"  ADB_HOST=192.0.2.1 make connect" \
		"  printf 'ADB_HOST=192.0.2.1\n' > .env && make connect" \
		"  make build" \
		"  ADB_HOST=192.0.2.1 make install" \
		"  ADB_SERIAL=192.0.2.1:5555 make install" \
		"  ADB_SERIAL=192.0.2.1:5555 make uninstall"

check:
	@$(RESOLVE_ADB_SERIAL_SH); \
	echo "ADB device connected: $$serial"

list:
	@$(ADB) devices

connect:
	@if [ -z "$(ADB_HOST)" ]; then \
		echo "ADB_HOST is not set."; \
		echo "Set it for this shell: export ADB_HOST=192.0.2.1"; \
		echo "Or create ./.env (next to this Makefile) with: ADB_HOST=192.0.2.1"; \
		echo "Then re-run: make connect"; \
		exit 1; \
	fi
	@read -r -p "Port: " port; \
	if [ -z "$$port" ]; then \
		echo "Port is required."; \
		exit 1; \
	fi; \
	$(ADB) connect $(ADB_HOST):$$port; \
	echo "Tip: if multiple devices are connected, run 'ADB_SERIAL=$(ADB_HOST):$$port make install'"

build:
	$(GRADLE) assembleDebug

install:
	@$(RESOLVE_ADB_SERIAL_SH); \
	echo "Installing to $$serial"; \
	if [ ! -f $(DEBUG_APK) ]; then \
		$(MAKE) build; \
	fi; \
	$(ADB) -s "$$serial" install -r $(DEBUG_APK)

uninstall:
	@$(RESOLVE_ADB_SERIAL_SH); \
	echo "Uninstalling from $$serial"; \
	$(ADB) -s "$$serial" uninstall $(APPLICATION_ID)
