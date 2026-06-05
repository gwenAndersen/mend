# Termux/Android Compilation Environment Reference

This document outlines the specific versions and configurations required for a successful Android build within this Termux environment (ARM architecture).

## 🛠️ Core Toolchain
These versions are confirmed to work together in this environment.

| Component | Version | Notes |
| :--- | :--- | :--- |
| **Java (JDK)** | 21.0.x | `openjdk-21` installed via Termux. |
| **Gradle Wrapper** | 8.13 | Defined in `gradle-wrapper.properties`. |
| **Android Gradle Plugin (AGP)** | 8.12.1 | Defined in the top-level `build.gradle`. |
| **Kotlin** | 2.0.0 | Required for Compose 2.0.0 integration. |
| **Compose Compiler** | 2.0.0 | Integrated into Kotlin 2.0.0. |

## ⚙️ Critical Environment Overrides (`gradle.properties`)
Standard Android SDK binaries (like `aapt2`) often fail on Termux because they are compiled for x86_64. Use the following overrides:

```properties
# Use the Termux-native aapt2 binary
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

# Set Java Home for Gradle (Java 21 required for AGP 8+)
org.gradle.java.home=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk

# Increase heap size for stable compilation in Termux
org.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=512m
android.dexoptions.javaMaxHeapSize=4g

# Target and JVM Compatibility
kotlin.jvm.target=1.8
android.jvmTarget=21
```

## 🏗️ Android SDK Configuration (`app/build.gradle`)
| Setting | Value |
| :--- | :--- |
| **compileSdk** | 34 |
| **targetSdk** | 34 |
| **minSdk** | 26 |
| **Java Compatibility** | `JavaVersion.VERSION_1_8` |

## 🚀 Build Command
Always ensure `JAVA_HOME` is set correctly before building:

```bash
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
./gradlew assembleDebug
```

## ⚠️ Known Issues & Fixes
1. **AAPT2 Architecture Mismatch:** If you see `Syntax error: ")" unexpected`, it means Gradle is trying to use the SDK's x86_64 `aapt2`. Ensure `android.aapt2FromMavenOverride` is set correctly.
2. **Memory Crashes:** Termux environments can be memory-constrained. If the build fails with `Expiring Daemon`, ensure `org.gradle.jvmargs` does not exceed available RAM.
3. **NDK Strip Error:** If using native libraries (JNI/SO), add `doNotStrip "**/*.so"` to the `packaging` block in `build.gradle` to avoid errors with the incompatible `llvm-strip` in some SDK versions.
