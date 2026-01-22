-include .env

GRADLE := ./gradlew
ADB := adb
APPLICATION_ID := com.example.gymprogress
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: check build install list connect

check:
	@$(ADB) devices | awk 'NR==1{next} /device$$/{found=1} END{if(found) print "ADB device connected."; else {print "No ADB device detected."; exit 1}}'

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
	$(ADB) connect $(ADB_HOST):$$port

build:
	$(GRADLE) assembleDebug

install: check
	@if [ ! -f $(DEBUG_APK) ]; then \
		$(MAKE) build; \
	fi
	$(ADB) install -r $(DEBUG_APK)
