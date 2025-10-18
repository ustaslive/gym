GRADLE := ./gradlew
ADB := adb
APPLICATION_ID := com.example.gymprogress
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: check build install

check:
	@$(ADB) devices | awk 'NR==1{next} /device$$/{found=1} END{if(found) print "ADB device connected."; else {print "No ADB device detected."; exit 1}}'

build: $(DEBUG_APK)

$(DEBUG_APK):
	$(GRADLE) assembleDebug

install: check $(DEBUG_APK)
	$(ADB) install -r $(DEBUG_APK)
