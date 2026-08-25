#!/bin/bash
./gradlew composeApp:installDevDebug
adb shell am start -n dev.nichidori.saku.dev/dev.nichidori.saku.MainActivity

