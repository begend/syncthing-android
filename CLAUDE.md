# Syncthing-Fork Android - Claude Code Context

## Project Overview

**Syncthing-Fork** is an Android wrapper around the native [Syncthing](https://github.com/syncthing/syncthing) daemon. The repository mainly contains the Android app layer, plus a helper Gradle module that builds the native binary into `app/src/main/jniLibs/`.

- **Application ID**: `com.github.catfriend1.syncthingfork`
- **Android namespace / Java package root**: `com.nutomic.syncthingandroid`
- **License**: MPLv2
- **Min SDK**: 23
- **Compile / Target SDK**: 36
- **Languages**: Java + Kotlin
- **JDK**: 21
- **Build System**: Gradle Kotlin DSL
- **Modules**: `:app`, `:syncthing`

This is still primarily a Syncthing wrapper app, but the current repository also contains newer Kotlin code for:

- an experimental / in-progress `webdav/` sync subsystem
- E-Ink device detection and UX optimizations
- a Compose-based settings experience using Navigation 3

## Quick Reference

### Safe Gradle Commands

```bash
# Always set this for agent/dev builds unless you explicitly need native rebuilds.
export IS_COPILOT=true

# Lint
IS_COPILOT=true ./gradlew lintDebug

# Unit tests
IS_COPILOT=true ./gradlew testDebugUnitTest

# Debug APK
IS_COPILOT=true ./gradlew assembleDebug

# Clean
./gradlew clean
```

### Important Build Rules

- Use **debug** builds for normal development.
- Do **not** rely on release builds locally unless signing material is configured.
- `IS_COPILOT=true` skips `:syncthing:buildNative`, which otherwise runs a Python-based native build.
- Avoid `assembleDebug` unless necessary; prefer smaller verification tasks.

## Key Files

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | Android app module config, dependencies, signing, and native-build wiring |
| `syncthing/build.gradle.kts` | Defines `buildNative` task that runs `build-syncthing.py` |
| `app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java` | Core foreground service that manages the native Syncthing process |
| `app/src/main/java/com/nutomic/syncthingandroid/service/RestApi.java` | Main wrapper around Syncthing's REST API |
| `app/src/main/java/com/nutomic/syncthingandroid/util/ConfigXml.java` | XML config parsing and mutation |
| `app/src/main/java/com/nutomic/syncthingandroid/settings/SettingsNavigation.kt` | Compose settings navigation using Navigation 3 |
| `app/src/main/java/com/nutomic/syncthingandroid/webdav/` | Experimental WebDAV sync-related implementation and E-Ink integration helpers |
| `app/src/main/AndroidManifest.xml` | Components, permissions, exported surfaces, foreground services |
| `gradle/libs.versions.toml` | Central dependency and version catalog |
| `.github/workflows/test.yml` | CI runs unit tests, lint, and debug build verification |

## Architecture

### Module Layout

**`:app`**
- Main Android application.
- Contains the Java-heavy legacy UI/service code and newer Kotlin-based settings / WebDAV / E-Ink code.

**`:syncthing`**
- Helper Gradle module, not a user-facing Android module.
- Provides `buildNative`, which invokes `python3 ./build-syncthing.py`.
- Writes native artifacts into `app/src/main/jniLibs/`.
- Uses the Syncthing upstream Git submodule at `syncthing/src/github.com/syncthing/syncthing`.

### Core Runtime Flow

**`SyncthingService`**
- Foreground `Service` that owns the lifecycle of the native binary.
- Coordinates:
  - `SyncthingRunnable` to launch/stop the daemon
  - `RestApi` to talk to the daemon over HTTP
  - `EventProcessor` to consume Syncthing events
  - `RunConditionMonitor` to decide whether syncing should run
  - notification / quick settings / startup behavior

**`RestApi`**
- Reads and updates Syncthing config via REST.
- Tracks devices, folders, statuses, completions, and paused state.
- Applies custom run conditions.
- Broadcasts folder sync completion to external apps.

**Config Layer**
- `ConfigXml.java`: low-level XML parsing and editing.
- `ConfigRouter.java`: higher-level config operations such as pausing, ignoring, and other convenience flows.

### UI Split

**Legacy UI**
- Main screens still live in classic `Activity` / `Fragment` / adapter code.
- Important directories: `activities/`, `fragments/`, `views/`.

**Modern Settings UI**
- Settings screens are Kotlin + Compose Material 3.
- Navigation uses `androidx.navigation3` (`NavDisplay`, `NavBackStack`), not the older Navigation Compose artifact.
- License display uses AboutLibraries Compose.

### Dependency Injection

The app still uses **Dagger 2**:

- `SyncthingApp.java`
- `SyncthingModule.java`
- `DaggerComponent.java`

Annotation processing is done through **KSP**.

### Project Map

Use this mental model when navigating the repo:

```text
Android app process
├── Application bootstrap
│   └── SyncthingApp -> builds Dagger graph
├── Entry UI
│   ├── FirstStartActivity -> permissions + initial config generation
│   ├── MainActivity -> main tabs/drawer UI, starts foreground service
│   └── SettingsActivity -> Compose settings, binds to service state
├── Service layer
│   └── SyncthingService
│       ├── RunConditionMonitor
│       ├── SyncthingRunnable
│       ├── RestApi
│       ├── EventProcessor
│       └── NotificationHandler
├── Config / storage
│   ├── ConfigXml
│   ├── ConfigRouter
│   └── SharedPreferences / app files / backup ZIP
└── Native Syncthing daemon
    └── libsyncthingnative.so + embedded Web GUI / REST API
```

### Core Call Chains

#### Cold Start

Typical first-run / normal-launch flow:

1. `SyncthingApp` creates the Dagger component.
2. Launcher opens `FirstStartActivity`.
3. `FirstStartActivity` checks storage / notification / doze / location prerequisites.
4. If config or keys are missing, `ConfigXml.generateConfig()` is triggered from the welcome flow.
5. `FirstStartActivity.startApp()` opens `MainActivity` (optionally chaining into `WebGuiActivity`).
6. `MainActivity.onCreate()` explicitly starts `SyncthingService`.
7. `SyncthingActivity.onResume()` binds activities to `SyncthingService`.

#### Service Startup To ACTIVE

Normal service transition:

1. `SyncthingService.onCreate()` initializes helpers and checks storage permission.
2. `SyncthingService.onStartCommand()` creates `RunConditionMonitor` on first start.
3. `RunConditionMonitor.updateShouldRunDecision()` evaluates power / wifi / metered / roaming / schedule / force-start logic.
4. If syncing should run, `SyncthingService.onShouldRunDecisionChanged(true)` calls `launchStartupTask()`.
5. `launchStartupTask()` loads `ConfigXml`, checks Web GUI port conflicts, creates `RestApi`, and starts `SyncthingRunnable` on a dedicated thread.
6. `SyncthingRunnable` launches `libsyncthingnative.so` with environment variables such as `STHOMEDIR`, `STNOUPGRADE`, `STTRACE`, proxy settings, and gateway fallback.
7. `PollWebGuiAvailableTask` waits until the embedded Web GUI / REST endpoint responds.
8. `RestApi.readConfigFromRestApi()` hydrates runtime state from the daemon.
9. `SyncthingService.onApiAvailable()` transitions service state to `ACTIVE`.
10. `EventProcessor.start()` begins polling Syncthing events and projecting them back into app state / notifications.

#### Ongoing Runtime

Once active, the layers interact like this:

- `RunConditionMonitor` keeps reevaluating global and per-object sync conditions.
- `RestApi` is the app's in-memory source for devices, folders, completion, pause state, and config-derived data.
- `EventProcessor` consumes Syncthing events and updates `RestApi`, notifications, MediaStore, and approval prompts.
- Activities and fragments bind to `SyncthingService` through `SyncthingActivity` and observe state changes.
- `SettingsActivity` uses Compose but still depends on the same bound `SyncthingService`.

#### Boot / Background Start

System-triggered restart path:

1. `BootReceiver` listens for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
2. If `PREF_START_SERVICE_ON_BOOT` is enabled, it starts `SyncthingService` using `startForegroundService()` on Android O+.
3. The rest of the flow is the same as a manual service start: run-condition evaluation first, then native launch only if allowed.

#### Shutdown / Restart

Important teardown behavior:

- User/UI restart goes through `SyncthingService.ACTION_RESTART`.
- Native-triggered restart is detected by `SyncthingRunnable` exit code `3`, which re-issues a service restart intent.
- Unexpected native exits trigger `ACTION_STOP` with `EXTRA_STOP_AFTER_CRASHED_NATIVE=true`.
- `SyncthingService.shutdown()` tears down pollers, `EventProcessor`, `RestApi`, notifications, and the native process thread in that order.

## Experimental / Newer Areas

### WebDAV Package

`app/src/main/java/com/nutomic/syncthingandroid/webdav/` currently contains:

- `WebDAVClient.kt`
- `WebDAVConfigManager.kt`
- `SyncEngine.kt`
- `ConflictResolver.kt`
- `model/WebDAVModels.kt`
- `EInkIntegration.kt`
- `EInkSyncManager.kt`
- `EInkNotificationManager.kt`
- `EInkConfigProvider.kt`

This code introduces:

- WebDAV connection/config management
- sync modes and conflict resolution
- Kotlin coroutine-based sync orchestration
- E-Ink-aware throttling and UX adjustments

### E-Ink Support

E-Ink-related code lives across:

- `util/EInkUtil.kt`
- `theme/EInkTheme.kt`
- `webdav/EInk*.kt`

These files appear focused on:

- device detection
- high-contrast / reduced-refresh UX
- batching updates and notifications for E-Ink devices

## Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Android Gradle Plugin | AGP | 9.1.0 |
| Kotlin | Kotlin | 2.3.20 |
| Java | JDK | 21 |
| UI | Jetpack Compose + Material 3 | current via catalog |
| Settings Navigation | Navigation 3 | 1.0.1 |
| DI | Dagger | 2.59.2 |
| KSP | Google KSP | 2.3.6 |
| Permissions | Accompanist Permissions | 0.37.3 |
| Networking | Volley | 1.2.1 |
| JSON | Gson | 2.13.2 |
| Compression | Zip4j | 2.11.6 |
| QR | ZXing Android Embedded + ZXing Core | 4.3.0 / 3.3.0 |
| WebDAV client | Sardine Android | 5.10 |
| Persistence deps | Room runtime / ktx / compiler | 2.6.1 |
| Serialization | kotlinx.serialization | 1.10.0 |

## Testing

### Current Automated Checks

CI in `.github/workflows/test.yml` runs:

- `IS_COPILOT=true ./gradlew testDebugUnitTest`
- `IS_COPILOT=true ./gradlew lintDebug`
- `IS_COPILOT=true ./gradlew assembleDebug`

### Current Test Areas

Existing unit tests currently focus mostly on newer Kotlin code:

- `app/src/test/java/com/nutomic/syncthingandroid/webdav/model/WebDAVModelsTest.kt`
- `app/src/test/java/com/nutomic/syncthingandroid/webdav/WebDAVConfigManagerTest.kt`
- `app/src/test/java/com/nutomic/syncthingandroid/webdav/WebDAVClientTest.kt`
- `app/src/test/java/com/nutomic/syncthingandroid/webdav/ConflictResolverTest.kt`
- `app/src/test/java/com/nutomic/syncthingandroid/webdav/SyncEngineTest.kt`
- `app/src/test/java/com/nutomic/syncthingandroid/util/EInkUtilTest.kt`

There are also `*.disabled` copies of several WebDAV tests in the repo. Treat them as inactive leftovers unless you intentionally revive them.

### Manual Testing

Useful emulator Web GUI access:

```bash
adb forward tcp:18384 tcp:8384
```

Then open `https://127.0.0.1:18384`.

## Important Development Notes

### Do

- Always set `IS_COPILOT=true` for normal Gradle verification.
- Use debug flavor for local development.
- Keep code and code comments in English.
- Prefer focused verification (`lintDebug`, `testDebugUnitTest`) over full builds.
- Check `wiki/manufacturer-specific/` and `wiki/known-bug-workarounds/` when changing device-specific behavior.

### Do Not

- Do not upgrade Kotlin in `gradle/libs.versions.toml` without a deliberate migration plan.
- Do not edit `wiki/CHANGELOG.md`.
- Do not assume release signing is available locally.
- Do not claim the repo is pure Java; newer Kotlin features are now significant in settings and WebDAV/E-Ink areas.

### Repository-Specific Notes

- `settings.gradle.kts` uses Aliyun mirrors, `mavenLocal()`, JitPack, GitHub Packages, plus standard Google / Maven Central repositories.
- `app/build.gradle.kts` validates that `versionCode` matches the 4-part `versionName`.
- Native builds are attached to merge JNI folder tasks unless `IS_COPILOT=true`.
- ABI splits are enabled only for release-style tasks.

## Current Caveats

These are useful to know before making assumptions:

- `AndroidManifest.xml` declares `.webdav.WebDAVSyncService`, but there is currently no matching `WebDAVSyncService.kt` in `app/src/main/java/.../webdav/`.
- Room dependencies are present, but there is currently no obvious `RoomDatabase` implementation in the main source tree.
- The codebase mixes mature legacy Syncthing wrapper code with newer in-progress Kotlin features; inspect wiring before assuming a feature is fully integrated.

## File Structure

```text
app/src/main/java/com/nutomic/syncthingandroid/
├── activities/      # Legacy activities
├── fragments/       # Legacy fragments
├── http/            # HTTP helpers
├── model/           # Syncthing model objects
├── receiver/        # Broadcast receivers
├── service/         # Foreground service and sync orchestration
├── settings/        # Compose settings screens + Navigation 3
├── theme/           # App theme and E-Ink theme support
├── util/            # Utilities, config helpers, E-Ink helpers
├── views/           # Classic adapters/custom views
└── webdav/          # Experimental WebDAV sync and E-Ink sync helpers

app/src/test/java/com/nutomic/syncthingandroid/
├── util/            # E-Ink utility tests
└── webdav/          # WebDAV-focused unit tests

app/src/main/
├── cpp/             # JNI / ndk-build entrypoint
├── res/             # Android resources
└── AndroidManifest.xml

syncthing/
├── build.gradle.kts
├── build-syncthing.py
└── src/github.com/syncthing/syncthing/  # Upstream submodule path
```

## Security / Platform Considerations

- Syncthing REST access is API-key based.
- Network security config lives in `res/xml/network_security_config.xml`.
- The app uses a foreground service and requests the associated modern Android permissions.
- Storage, location, camera, battery optimization, and notification permissions are all part of the runtime surface.
- External apps can subscribe to sync-completion broadcasts via `${applicationId}.permission.RECEIVE_SYNC_STATUS`.

## Resources

- `README.md`
- `wiki/README.md`
- `wiki/developers/Building-and-Development.md`
- `wiki/migration/Switching-from-the-deprecated-official-version.md`
- `wiki/tips-and-tricks/`
- `privacy-policy.md`
- `EINK_OPTIMIZATION.md`
- `WEBDAV_EINK_SYNC_REDESIGN.md`
- `WEBDAV_EINK_PHASE1_TASKS.md`
- `WEBDAV_EINK_OPTIMIZATION_TRACKER.md`
- `TEST_SUMMARY.md`

## License

This project is licensed under MPLv2. See `LICENSE`.

Forked from: [syncthing/syncthing-android](https://github.com/syncthing/syncthing-android)
