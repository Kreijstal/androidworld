# Hello World Android (No Android Studio)

Build an Android APK from source using only `javac` and open-source tools. No Android Studio, no Gradle, no SDK Manager, no binary downloads.

**Tested and confirmed working on a real device.**

## Prerequisites

- JDK 8+ (`javac`, `jar`, `keytool`)
- `make`
- `git`

## Setup

```sh
git clone --recurse-submodules https://github.com/Kreijstal/androidworld.git
cd androidworld
```

If you already cloned without `--recurse-submodules`:

```sh
git submodule update --init --recursive
```

## Build

```sh
make
```

This produces `build/app.apk` — a signed Hello World APK (v1+v2+v3 signatures).

You can customize the build:

```sh
make MIN_SDK=21 TARGET_SDK=28 VERSION_CODE=1 VERSION_NAME=1.0
```

## Install

```sh
adb install build/app.apk
```

## How It Works

The entire build uses `javac` and `jar` from the JDK. All tools are compiled from source with zero external dependencies — nothing is downloaded at build time.

| Step | Tool | Source |
|------|------|--------|
| Compile `.java` → `.class` | `javac` | JDK |
| Convert `.class` → `.dex` | `dx` | [JakeWharton/dalvik-dx](https://github.com/JakeWharton/dalvik-dx) (AOSP) |
| Encode `AndroidManifest.xml` | `BinaryXmlEncoder` | `tools/src/` (custom) |
| Package APK | `ApkBuilder` | `tools/src/` (custom) |
| Sign APK (v1+v2+v3) | `apksigner` | [aosp-mirror-neo/platform_tools_apksig](https://github.com/aosp-mirror-neo/platform_tools_apksig) |
| Generate keystore | `keytool` | JDK |

Every tool in the pipeline is either part of the JDK or compiled from source during the build. The `dx` dex compiler (408 Java files) and `apksigner` (135 Java files) have zero external dependencies — they compile with bare `javac`.

### Submodules

| Path | Repo | Purpose |
|------|------|---------|
| `vendor/android-platforms` | [Sable/android-platforms](https://github.com/Sable/android-platforms) | `android.jar` (compile-time SDK stubs) |
| `vendor/dalvik-dx` | [JakeWharton/dalvik-dx](https://github.com/JakeWharton/dalvik-dx) | Dex compiler (`.class` → `.dex`) |
| `vendor/apksig` | [aosp-mirror-neo/platform_tools_apksig](https://github.com/aosp-mirror-neo/platform_tools_apksig) | APK signing (v1/v2/v3) |
| `vendor/ARSCLib` | [REAndroid/ARSCLib](https://github.com/REAndroid/ARSCLib) | Resource/binary XML library (available, not yet used) |

## Clean

```sh
make clean
```

## Project Structure

```
app/
├── AndroidManifest.xml                    # Plain XML manifest
└── src/com/example/helloworld/
    └── MainActivity.java                  # Hello World activity
tools/
└── src/com/example/build/
    ├── BinaryXmlEncoder.java              # Encodes manifest to Android binary XML
    └── ApkBuilder.java                    # Packages APK (ZIP with manifest + dex)
vendor/
├── android-platforms/                     # android.jar stubs (submodule)
├── apksig/                                # APK signer source (submodule)
├── dalvik-dx/                             # Dex compiler source (submodule)
└── ARSCLib/                               # Resource library source (submodule)
Makefile                                   # Build everything with make
```
