---
name: gradlew-needs-java-home-jbr
description: "Terminal ./gradlew fails with \"Unable to locate a Java Runtime\" unless JAVA_HOME points to the Android Studio JBR (Java 21) — no standalone JDK on this machine"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 2047cca1-6990-45a8-8d05-a871907485c1
  modified: 2026-07-21T02:25:29.733Z
---

මේ machine එකේ standalone JDK එකක් **නෑ** (`/usr/libexec/java_home` හිස්). Terminal එකෙන් `./gradlew` run කරද්දී **"Unable to locate a Java Runtime"** ලෙස fail වෙනවා. Fix: `JAVA_HOME` එක Android Studio JBR එකට set කරන්න —

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # openjdk 21.0.10, JetBrains
```

මෙය Gradle 8.14.5 Daemon JVM toolchain (`gradle/gradle-daemon-jvm.properties`, `toolchainVersion=21`) එකට ගැළපෙනවා. Verified 2026-07-21: `./gradlew clean assembleTranzlateOfflineDebug` → `BUILD SUCCESSFUL` (66/66 tasks). Android Studio ඇතුළෙන් build කරද්දී JBR එක auto-use වෙන නිසා මේ ප්‍රශ්නය එන්නේ terminal එකෙන් `./gradlew` දාද්දී විතරයි. See [[no-speculation-verified-data]].
