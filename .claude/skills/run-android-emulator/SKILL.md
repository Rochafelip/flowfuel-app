---
name: run-android-emulator
description: Use when asked to run, launch, or test the FlowFuel app on an Android emulator on this machine (Windows, RTX 5060 Ti) — boots the Pixel_6 AVD, builds+installs the debug APK, and launches MainActivity
---

# Running FlowFuel on the Android Emulator

## Known issue: GPU host mode hangs on boot

On this machine, the emulator's default GPU mode (`host` / gfxstream, used
automatically when `-gpu` is omitted) hangs indefinitely during startup —
the `qemu-system-x86_64.exe` process launches and sits at constant memory
usage forever, never opening the ADB port. This happens both with and
without `-no-window`. Root cause is unconfirmed (suspected WHPX +
gfxstream + RTX 5060 Ti driver interaction) but the workaround is reliable:
always pass `-gpu swiftshader_indirect` (software rendering) explicitly.

Do not spend time re-diagnosing this — just use the swiftshader flag.

## Environment

- SDK root: `C:\Users\rocha\AppData\Local\Android\Sdk` (from `local.properties: sdk.dir`)
- Emulator binary: `$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe`
- ADB binary: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`
- AVD name: `Pixel_6`
- Package (debug build): `com.flowfuel.app.debug` (release id is `com.flowfuel.app`, debug adds `.debug` suffix)
- Main activity: `com.flowfuel.app.MainActivity`

## Steps

1. **Check for stray processes first.** If a previous run crashed/hung, leftover
   `qemu-system-x86_64.exe` / `emulator.exe` processes will block a new one with
   `FATAL | Running multiple emulators with the same AVD`. Clean up if needed:
   ```bash
   taskkill //F //IM qemu-system-x86_64.exe 2>/dev/null
   taskkill //F //IM emulator.exe 2>/dev/null
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" kill-server
   ```

2. **Launch the emulator** with a visible window and software rendering, detached
   so it survives the tool call:
   ```bash
   "$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Pixel_6 \
     -gpu swiftshader_indirect -no-snapshot -netdelay none -netspeed full \
     > /c/Users/rocha/AppData/Local/Temp/claude/emu_window.log 2>&1 &
   disown
   ```

3. **Wait for boot to complete** (first cold boot can take 1-2 min):
   ```bash
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" wait-for-device shell \
     'while [[ -z $(getprop sys.boot_completed | tr -d "\r") ]]; do sleep 2; done; echo BOOT_COMPLETED'
   ```

4. **Build and install the debug APK:**
   ```bash
   cd "C:\Users\rocha\AndroidStudioProjects\flowfuel-app"
   ./gradlew.bat installDebug -x lint --console=plain
   ```

5. **Launch the app:**
   ```bash
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell monkey \
     -p com.flowfuel.app.debug -c android.intent.category.LAUNCHER 1
   ```

6. **Verify it's actually on screen** (don't just trust the install/launch exit code):
   ```bash
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell dumpsys activity activities \
     | grep -i topResumedActivity
   # expect: com.flowfuel.app.debug/com.flowfuel.app.MainActivity
   ```
   Then screencap and look at it:
   ```bash
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > <scratchpad>/app_screenshot.png
   ```
   Read the screenshot — a blank/white frame means the launch didn't actually render.

## Notes

- `adb` may need `kill-server` / `start-server` if it doesn't see the device
  even though the qemu process is alive and listening.
- The emulator window, once up with swiftshader, stays visible and interactive
  on the user's desktop — this is a real GUI window, not headless, so the user
  can use it directly after you hand off.
