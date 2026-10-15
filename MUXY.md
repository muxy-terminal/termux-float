# muxy organization build

This fork is part of [muxy-terminal](https://github.com/muxy-terminal). Install release APKs signed with the muxy certificate, not upstream debug builds. Package names, shared UID, and Android permission checks are preserved.

Clone the muxy repository beside this addon and check out the revision in `MUXY_CORE.txt`. Use Java 17 or 21, the addon Gradle wrapper, Android SDK 36, NDK 29.0.14206865, and Zig 0.16.0. Build with `./gradlew :app:assembleRelease`. Library projects use muxy SDK settings; the addon keeps its own minimum SDK.

Float uses muxy libghostty-vt, CJK composition, cursor anchors, and native key encoding. Service shutdown disposes native state. Styling broadcasts update native colors. The instrumentation suite checks the actual Float PTY, CJK input, repeated deletion, navigation, colors, and closure. Live Gboard tests require `gboardExpected` and externally driven key taps.

Release APKs use certificate SHA-256 `756768af514d3fa0d1c214d54dabd7b0cd088a1723a22f520b90e8e8545262ce`. CI produces unsigned build artifacts and verifies published signed APKs. Private signing material is not stored here.
