# AvdBuddy desktop app

Run the following commands from the directory containing this README.

A Kotlin desktop app for managing Android emulators without opening Android Studio.
Built with Compose Desktop, a generated AvdBuddy theme, and JetBrains Jewel controls. No Material UI.

The application targets macOS, Windows, and Linux. Install an Android SDK with
command-line tools, emulator, and platform-tools for your host. Running emulators
requires the Android Emulator's normal hardware acceleration, runtime libraries, and graphics support
(Windows hypervisor support or Linux KVM as appropriate).

On macOS, scrollable content uses a custom bounce effect enabled by the app theme.
Its resistance and return curve are based on direct AppKit measurements.
The effect handles AWT trackpad scrolling as well as Foundation scroll callbacks.
Use `LazyColumn`, `LazyRow`, `verticalScroll`, and `horizontalScroll` from
`com.avdbuddy.theme`. These shared APIs derive the bounce axis from their
own scroll direction; screens do not pass orientation or overscroll configuration.
Windows and Linux retain their default scrolling behavior.

## Run

Run with Compose Hot Reload:

```bash
./gradlew hotRun --auto
```

On Windows, use `.\gradlew.bat hotRun --auto` in PowerShell.

Keep this process running and save Kotlin UI changes. Compose Hot Reload recompiles
and updates the running app while retaining its state. It uses the JBR 25 toolchain;
normal packaged builds (`createDistributable`) do not enable Hot Reload.

Supported application dependencies and project plugins are managed by KPM in `package.yml`.
Use `kpm list` to inspect dependencies and `kpm i group:artifact:version` to add one.
KPM resolves and applies the manifest's plugins; the app's `plugins` block only
bootstraps KPM. Versions are specified directly in each plugin and dependency
declaration.

Build tooling has its own `buildSrc/package.yml` for plugins and dependencies.
Those dependencies belong to the build tooling. Neither build script parses
YAML or hardcodes those versions. Settings plugins, desktop platform selection, and
test-only dependencies remain in Gradle.

The build requires a JDK to start Gradle. The Gradle toolchain resolver provisions
JBR 25, which Jewel requires. Xcode is not required for development.

## Theme experiment

Edit `theme.yml` to define colors and typography. Colors are quoted `#RRGGBB` or
`#AARRGGBB` values. Each typography entry has `fontFamily`, `fontSize`, `fontWeight`,
and `lineHeight`; sizes and line heights are in sp.

```yaml
colors:
  text: "#000000"
typography:
  body:
    fontFamily: Inter
    fontSize: 13
    fontWeight: 400
    lineHeight: 18
```

`generateTheme` runs automatically before Kotlin compilation and writes typed
Kotlin into `build/generated/theme/kotlin`. Run `./gradlew generateTheme` to generate
without compiling, or `./gradlew compileKotlin` after editing YAML. Generated files
are build outputs and should not be edited or committed. New token names become
available after generation; existing bindings in `theme/AvdBuddyTheme.kt` and
`theme/AvdBuddyStyles.kt` expect the app's current semantic names.

App code reads `AvdBuddyTheme.colors.shelfBackground` and
`AvdBuddyTheme.typography.body`. Typography entries generate complete `TextStyle`s.
Google Fonts loads each family with the weights used by its entries, using the
library's fallback while loading or when loading fails. Fonts load at runtime;
there is no font download during code generation.

Jewel controls remain in place. Their style APIs and token bindings stay in Kotlin.
The adapter supplies global colors and body typography to Jewel; popup colors and
explicit app text styles also consume generated values. Other Jewel component
styles keep their existing defaults. This experiment supports colors and
typography only.

Generator tests run headlessly with `./gradlew buildSrc:test`.

## Features

- One application window, with a two-panel Settings view and in-app dialogs for emulator actions
- Native file and folder pickers through FileKit; do not implement custom pickers
- Emulator sidebar and a large, framed emulator preview
- Top-right pin toggles keeping the window above other windows
- Select emulators without starting them; stopped emulators show their icon, name, and a Start button
- Central Play button boots/resumes the selected emulator; new launches run inside the app without a separate emulator window
- Native live screen streaming with immediate touch/drag input and basic ASCII keyboard input
- Host GPU acceleration for all launches, new emulators, and duplicates
- Right-click or use the emulator menu for duplicate, rename, delete, and Show in Finder / File Explorer / File Manager
- In-app emulator creation dialog: name, Pixel phone profile, and installed Android versions read locally without refreshing the online catalog
- Google Play services included, with host-native images selected automatically; Play Store images are preferred when available
- Create closes the dialog and shows setup in the emulator preview, then starts the emulator automatically
- Download missing Android images during setup, with storage checks, cancellation, and retry
- Settings sections for Your License, SDK Locations, Android Images, and Updates; Back to app restores the emulator workspace
- Android Images groups versions into Installed and Available when images are installed, otherwise shows one list, newest first, with download/delete buttons and per-version progress; downloads continue when leaving the section
- Android image archives download in parallel; completed archives wait for one-at-a-time installation through the Android SDK
- Settings cog rotates during Android downloads and installation; click it to view Android Images. Failed downloads show an error dot.
- Separate executable paths for adb, emulator, sdkmanager, and avdmanager, with SDK defaults and support for split Homebrew installations
- Running-emulator detection
- Diagnostics and Nucleus automatic update checks
- Keyboard shortcuts: Cmd on macOS, Ctrl on Windows/Linux; B to toggle the sidebar, N to create, comma for Settings, Backspace to delete, and left/right arrows to rotate

On macOS, Settings imports SDK and AVD folder preferences from the previous Swift
app on first use. Saved settings and update preferences use native config folders:

| Platform | App settings | Default SDK |
| --- | --- | --- |
| macOS | `~/Library/Application Support/AvdBuddy` | `~/Library/Android/sdk` |
| Windows | `%APPDATA%\AvdBuddy` | `%LOCALAPPDATA%\Android\Sdk` |
| Linux | `$XDG_CONFIG_HOME/AvdBuddy` (default `~/.config/AvdBuddy`) | `~/Android/Sdk` |

SDK discovery checks the saved path, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, then the
platform default. Emulator storage respects `ANDROID_AVD_HOME`,
`ANDROID_EMULATOR_HOME`, and `ANDROID_USER_HOME`, defaulting to `~/.android/avd`.
SDK, emulator, and ADB locations use host-native paths and accept spaces.
Settings → SDK Locations lets you override each tool's executable independently.
Blank tool fields use their location within the selected SDK. The SDK folder remains
the destination for Android images even when command-line tools are installed elsewhere.

Switching emulator folders does not move existing emulators or change Android Studio's
settings. System images remain under the selected SDK's `system-images` folder.
The app creates regular directories and does not create symlinks. Offline linked
storage is reported before an install starts.

For command-line diagnostics, run the packaged app executable with `--diagnostics`.

The selected emulator's live view uses its authenticated gRPC screen stream.
Raw RGBA frames go directly to Skia, with stale frames dropped instead of queued.
Touch presses, movement, and releases use the same persistent connection; keyboard
input still uses ADB. No screenshot subprocesses or PNG writes run per live frame.
Only the selected emulator streams. Multi-touch is not supported.

AvdBuddy launches emulators with `-gpu host` and a token-protected gRPC endpoint, and
writes `hw.gpu.enabled=yes` / `hw.gpu.mode=host` for newly created and duplicated
AVDs. Existing emulators also use host rendering when launched through AvdBuddy.
Emulators started by an older AvdBuddy build need to be stopped and started again.
Externally launched emulators require a discovery file with a gRPC port and bearer
token to stream; otherwise the preview asks for a restart through AvdBuddy.

The preview shows only fresh frames from the selected emulator's live stream.
Frames are discarded when switching emulators or when the stream ends; screenshots
are never loaded from disk or saved on shutdown. Android's normal Quick Boot state
loading and saving remains enabled.

## Emulator profiles

Built-in profiles live in `src/main/resources/profiles/*.yml`. The catalog includes
Pixel 10, Pixel 10a, Pixel 10 Pro, and Pixel 10 Pro XL, each with matching display dimensions and frame artwork.
RAM and storage match the published base configurations:

| Profile | RAM | Storage capacity |
| --- | --- | --- |
| [Pixel 10](https://store.google.com/product/pixel_10_specs) | 12 GB | 128 GB |
| [Pixel 10a](https://store.google.com/product/pixel_10a_specs) | 8 GB | 128 GB |
| [Pixel 10 Pro](https://store.google.com/product/pixel_10_pro_specs) | 16 GB | 128 GB |
| [Pixel 10 Pro XL](https://store.google.com/product/pixel_10_pro_specs) | 16 GB | 256 GB |

The emulator settings express these capacities in MiB and GiB. Existing emulators keep their configuration.

Storage is a virtual capacity; sparse device data grows as it is written. Creation budgets
up to 6 GiB of initial data and 5 GiB of startup/cache headroom,
plus the full capacity of any optional SD card. Missing system images also require their
download and extraction space. The storage review does not reserve space for future RAM
snapshots or require the full virtual capacity to be free.

Android versions are independent of the profile and have no physical-device support cap.
Only installed, stable images with Google Play services matching the host architecture are offered
in the creation dialog. Download other versions in Settings → Android Images. Equivalent SDK extension
variants share one entry. Google APIs images cover older versions without a Play Store image. Previews and cross-architecture
fallbacks are excluded from built-in emulator creation.

To add an emulator profile, add its YAML definition and matching `layout`, `back.webp`, and
`mask.webp` under `src/main/resources/device-frames/`, then register its ID in
`HardwareProfiles.all`. Keep display dimensions and frame placement consistent
with the skin layout. The catalog tests validate the bundled artwork and profile.
Custom profile creation/import and automatic installation of a missing Android SDK
are not part of this first creation-dialog implementation; SDK location remains
configurable in Settings.

Pixel frame files are bundled unmodified from the official JetBrains Android
plugin repository at a pinned revision. Their Apache 2.0 license and provenance
are distributed under `src/main/resources/licenses/`. Each created emulator gets
its own frame files, so SDK device definitions and installed SDK skins are not
required. Existing emulators keep their configurations.

## Verification

```bash
./gradlew test
```

Windows equivalent: `.\gradlew.bat test`.

The test command runs only backend tests. They use temporary SDK/AVD fixtures and run
headlessly, without opening windows or clicking through the app.

Use [Spectre](https://spectre.sebastiano.dev/) interactively against the running
development app for UI inspection. The installed Spectre CLI supplies its agent
when attaching; no Spectre dependency is packaged with the app. There are no runnable Compose/Spectre UI tests
and no UI test script. Do not add or run automated UI test suites.

```bash
./gradlew hotRun --auto
# In another terminal, find the AvdBuddy process and attach:
spectre ps
spectre attach <pid>
spectre windows <session-id>
spectre tree <session-id>
# Inspect or interact as needed, then detach:
spectre detach <session-id>
```

Interactive actions operate on the actual running app and its real emulators.

## Packaging

```bash
./gradlew createDistributable
./gradlew packageDmg packageZip
```

The `.app` is in `build/compose/binaries/main/app/`. Nucleus packages the DMG
and ZIP beneath `build/compose/binaries/main/`. Release publishing generates
the Nucleus-compatible update metadata after signing and notarization.
These commands only build local artifacts; they do not publish a GitHub release.

The installer formats are DMG/ZIP on macOS, NSIS on Windows, and AppImage on
Linux. Official downloads are at https://avdbuddy.com/download. The current
distribution targets Apple silicon and Intel on macOS, and x64 on Windows/Linux.

Installed builds check for updates through https://licenses.avdbuddy.com and
download installers from https://downloads.avdbuddy.com. Downloads are verified
against SHA-512 checksums; installation requires an explicit restart. Development
builds do not update themselves. You can disable automatic update checks.
Settings → Updates shows the installed version, update status, and license eligibility.
When the installed version is newer than the license covers, it offers to download
and restore the last eligible release through the same verified installer flow.
Automatic checks update the settings status without opening a dialog.

License activation is optional and does not restrict current features. Use
Settings → Your License to activate a key or open the license portal.
Unlicensed installations show a purchase reminder when the window regains focus
after at least 15 minutes away, at most once every 24 hours. Dismissing the reminder
starts a fresh 24-hour cooldown, saved across restarts. Startup does not trigger it,
and returning while Settings, another dialog, or a busy operation is open skips it.
Activation saves a signed receipt bound to this installation; the key is not
stored. The app verifies the receipt offline and does not poll activation status.
Updates use the receipt's entitlement date to select the newest eligible release.
An expired update entitlement never stops the app or invalidates an older version.
Unlicensed installations can receive updates while current features remain free.

For local checkout/activation verification, start a development app with
`AVDBUDDY_LICENSE_ORIGIN=http://127.0.0.1:8090`,
`AVDBUDDY_LICENSE_PUBLIC_KEY` set to the local service's public signing key, and
`AVDBUDDY_PURCHASE_URL=http://127.0.0.1:8080/pricing`, and
`AVDBUDDY_LICENSE_DIRECTORY` set to a separate absolute directory outside the
normal app settings directory. Run `./gradlew hotRun` with those variables.
Buy License opens the local website; Manage License opens the local license
portal. Overrides are
accepted only by Hot Reload development launches and only for a loopback HTTP
origin; installed releases keep the production origin and trusted public key.
Local receipts and device IDs never overwrite the normal installation's license.

The opt-in headless integration test `LocalLicenseActivationTest` uses
`AVDBUDDY_LOCAL_TEST_KEY_FILE`, `AVDBUDDY_LICENSE_ORIGIN` and
`AVDBUDDY_LICENSE_PUBLIC_KEY` to verify a purchased test key, offline receipt
reload, activation retry and one-device limits. Use a fresh sandbox license;
this test consumes its one device slot. It does not open an app or send email.
Optionally set `AVDBUDDY_LOCAL_TEST_DIRECTORY` to a fresh private directory to
retain the test receipt and device ID for inspecting the development app afterward.

For a signed macOS build, set `AVDBUDDY_MAC_SIGN_IDENTITY` to a Developer ID
Application identity in your Keychain and `AVDBUDDY_NOTARY_PROFILE` to a
`notarytool` Keychain profile. Run `packageDmg packageZip`, then `notarizeDmg
notarizeZip`. Packaging alone never uploads or publishes a release.

Inspired by [VirtualBuddy](https://github.com/insidegui/VirtualBuddy).
