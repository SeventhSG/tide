# Building

## The toolchain

Installed user-local, no admin, nothing on PATH. Set both variables for every Gradle
invocation or the build will not find the SDK.

| | |
|---|---|
| JDK 17 | `C:\Users\gamin\dev\jdk17` (Temurin 17.0.20.1) |
| Android SDK | `C:\Users\gamin\dev\android-sdk` (platform 35, build-tools 35, platform-tools 37) |
| Gradle | wrapper, 8.11.1. The standalone copy in `dev\gradle-8.11.1` only bootstrapped it. |

`local.properties` points at the SDK and is gitignored, so it stays on this machine.

```powershell
$env:JAVA_HOME="C:\Users\gamin\dev\jdk17"
$env:ANDROID_HOME="C:\Users\gamin\dev\android-sdk"
```

## Commands

```powershell
.\gradlew.bat :app:assembleDebug          # APK at app\build\outputs\apk\debug\
.\gradlew.bat :core:data:testDebugUnitTest # progression engine and repository tests
.\gradlew.bat :app:recordRoborazziDebug    # screenshots
```

```bash
bash tools/gate.sh                         # must pass before any commit
python tools/render_brand.py               # icon sizes, mask tests, palette
python tools/render_mock.py                # the ocean reference renders
```

## Screenshots

**There is no usable emulator on this machine, and this is not worth retrying.**

`emulator -accel-check` reports no hypervisor driver. Installing AEHD, or enabling Windows
Hypervisor Platform, both need admin and a reboot. Neither is available. Running with
`-accel off` does not work as a fallback: the process starts, sits at zero CPU seconds and
149 MB, and never boots. `adb` sees the port and the device stays `offline`.

An AVD exists (`tide_pixel`, Pixel 6, API 35, `google_apis/x86_64`) and the system image is
downloaded, so it looks ready. It is not. Booting it exits immediately with:

```
ERROR | x86_64 emulation currently requires hardware acceleration!
```

**The ARM route is closed too, and this is the part worth writing down**, because a system
image that boots under software emulation is the obvious thing to reach for next. It does
not work here. `arm64-v8a` images exist for API 26 and up, and Tide's `minSdk` is 26, so an
API 30 image should in principle run the app. Emulator 37.1.11 refuses:

```
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator
        on x86_64 host. System image must match the host architecture.
```

ARM-on-x86 translation was removed from the emulator. No admin, no driver, and no flag
changes that. The two routes that do work are admin plus a reboot, or a physical phone.

So screenshots come from **Roborazzi**, which renders Compose to PNG on the JVM through
Robolectric:

```powershell
.\gradlew.bat :app:recordRoborazziDebug
```

Output lands in `app\build\screenshots\`. It takes about thirty seconds and needs no device.

What it gives you is real: real Compose layout, the bundled Manrope and IBM Plex Mono, the
superellipse shapes, the ocean drawing, the measured colours.

What it cannot tell you: whether `MainActivity` launches cleanly, and how the nav's spring
and the bubbles feel in motion. Those need hardware. Two routes when a real device is
available:

- **A phone over USB** with debugging on, then `adb install -r app-debug.apk`. Thirty seconds
  and it is the real thing.
- **Enable the hypervisor**, one admin prompt plus a reboot, after which the emulator works
  properly. The system image and emulator are already installed, 4.6 GB of the 6 GB under
  `dev\`.

## Verifying rather than assuming

`BUILD SUCCESSFUL` is not evidence that anything ran. Three checks worth repeating:

- **Tests**: read `core/data/build/test-results/**/TEST-*.xml` for the actual counts. A task
  can succeed having run nothing.
- **Room**: check `core/data/schemas/` exported a JSON for the current version. If it did
  not, the annotation processor did not see the entities.
- **The test suite itself**: it has been mutation checked once. Breaking the progression
  engine's success condition on purpose made 4 tests fail. Worth redoing after a large change
  to the engine, because green tests prove nothing on their own.
