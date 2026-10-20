# Kilometrina 1.1.7: audit fixes and attachment recovery

Version code: **117**. Application ID remains `si.lukabencina.kilometrina`.
Room schema remains **6**; these fixes do not delete, rebuild, or migrate existing databases.

## Fixed defects

| Audit finding | Change | Regression coverage |
|---|---|---|
| F01: crash when an active/recovered trip finishes | `HomeScreen` animates the trip snapshot and renders the snapshot supplied to its content callback. Outgoing content no longer dereferences a newly null external trip. | `HomeTransitionSafetyTest` covers recovered completion and repeated transitions. |
| F02: duplicate ZIP receipt names and uncaught export errors | ZIP entries include a unique ordinal after sanitization/truncation. Reports generate into a temporary file on IO before writing the selected document; failures are displayed. | Six ZIP cases, including duplicate, sanitized, and truncated-name collisions; actual SAF export on R8. |
| F03: duplicate receipt IDs silently overwrite data on restore | Backup validation requires positive, unique attachment IDs before applying data. | Codec tests and a real Room restore test verify rejection and preservation of the previous database. |
| F04: opening and saving an editor rounds the stored distance | Preserve exact original distance and rate until the corresponding field is edited. | UI tests cover no-op save and explicit distance/rate edits. |
| F05: editing an old trip replaces its historical vehicle | Retain the original vehicle snapshot unless the user explicitly selects a vehicle. Historical and current versions are available separately when they differ. | UI tests cover unchanged historical vehicle and explicitly selecting the updated vehicle. |
| F06: interruption between a GPS point and distance update | Point insertion and distance increment share a Room transaction. | A database trigger injects a failure in the distance write; the point rolls back, and resumed tracking reconciles with segment totals. |
| F07: signed release workflow calls a nonexistent task | Use the supported `:app:testDebugUnitTest` task. Release lint, R8 build, signing identity checks, and AAB build remain separate steps. | The same unit-test task runs locally with 49 passing tests. Actual GitHub signing secrets/pipeline were not exercised. |
| F08: the app cannot restore its own long addresses or legacy rates | Keep complete historical addresses and finite nonnegative legacy trip rates in backups. New trip input uses the shared rate/number rules. | Own-backup roundtrip with a 501-character address and legacy rate; v1–v6 compatibility. |
| F09: NaN/Infinity and values outside the cents representation | Shared numeric validation checks finiteness, rate range, distance conversion, and per-expense storage bounds. | Independent tests for every invalid value in every field, including integer and floating-point boundaries. |
| F10: printed row amounts do not reconcile with the summary | Mileage is rounded per trip to cents with `HALF_UP`. Totals and statistics sum those rounded amounts; report sums use decimal arithmetic. | Two 100 m trips at 0.43 yield 0.04 + 0.04 = 0.08 in PDF and CSV. |
| F11: malformed route-stop JSON silently loses stops | Missing optional arrays remain backward compatible; present arrays with an invalid type are rejected. This also protects the attachments array. | Invalid strings, numbers, and null arrays are rejected; old missing fields still load. |
| F12: adding two Int expense values overflows | Convert cents to Long before adding. | Both expenses at `Int.MAX_VALUE` yield 42,949,672.94, including PDF/CSV checks. |
| F13: missing wrapper launchers and JAR | Add the Gradle 9.6.0 wrapper, distribution SHA-256, Windows/POSIX scripts, LF policy, and executable POSIX script. | Wrapper downloads/verifies the distribution and runs the final build/test tasks. |
| Phone incident: adding a large photo causes a persistent startup crash | Screen/detail flows query only attachment metadata. Existing BLOBs remain intact and are read in 256 KiB slices for backups and exports. Import reads a bounded stream on IO and reports errors. | An 8 MiB legacy BLOB reproduces the old CursorWindow failure, then survives reopen, backup/restore, and ZIP export with identical bytes. R8 is tested with the same size. |

An additional backup regression was fixed: current-schema trips without a vehicle no longer acquire today's default vehicle on restore. Default-vehicle backfilling remains limited to the legacy schema that predates vehicle snapshots.

The foreground-service type constant is guarded for API 26–28. The navigation receipt icon uses its supported auto-mirrored variant.

## Local verification, 20 October 2026

Environment: Windows, JDK 17, Gradle 9.6.0, compile SDK 37, API 35 x86_64 emulator. Android tests were run on the disposable emulator, not on the user's phone.

- Unit tests: **49 passed**; the original 37 tests remain passing.
- Android instrumentation: **9 passed** across six classes.
- Fresh database and synthetic legacy migration paths 1→6 through 5→6 passed native Room validation.
- PDF: **18/18 passed**, covering A–O from the audit, empty optional fields, rounding, and expense overflow. Selected pages were rendered and visually inspected.
- CSV: **18/18 passed**, including values, ordering, UTF-8, private-trip exclusion, and cent reconciliation.
- ZIP: **6/6 passed**, including all three collision cases that failed in the audit.
- Debug APK, instrumentation APK, minified release APK, and release AAB built successfully.
- Lint: **0 errors, 19 warnings**. Remaining warnings concern newer dependency/Gradle versions, target SDK recommendations, icons/resources, and KTX suggestions. They are retained in the validation log; dependency and target SDK upgrades are separate compatibility work.
- R8 smoke: launch, actual SAF backup, and actual monthly ZIP succeeded with an 8 MiB receipt and same-name receipts. All four exported receipt byte sequences matched the backup contents.

R8 runtime testing used a locally signed **emulator-only** copy. It is not a production update package.

## Repeating the checks

Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to an SDK containing the required platform and build tools.

```powershell
.\gradlew.bat --no-daemon --no-parallel :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lint :app:assembleRelease :app:bundleRelease
```

On a disposable, explicitly selected emulator:

```powershell
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w si.lukabencina.kilometrina.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 pull /sdcard/Android/data/si.lukabencina.kilometrina/files/release-regression ./local-export-results
python tools/release-checks/verify_exports.py --artifacts ./local-export-results
```

The optional independent PDF/CSV/ZIP checker needs Python and `pdfplumber`. It exits nonzero on a failed matrix case. `ExportMatrixTest` writes the fixture manifest alongside the actual native Android exports.

## Signing and installation

A new permanent release identity was explicitly authorized for 1.1.7 onward. The key is stored outside Git with restricted access. `docs/SIGNING_POLICY.md` records the public fingerprint and local/CI release procedure; `tools/build-signed-release.ps1` verifies APK/AAB signing and reuses that identity. GitHub signing secrets still require owner provisioning. Unsigned artifacts are never update packages.

The production APK passed apksigner verification (v2/v3); the AAB passed strict jarsigner verification against the approved keystore. The signed production APK installed, launched, and reinstalled successfully on the disposable API 35 emulator. A separate negative check confirmed that Gradle rejects the machine's unrelated debug certificate. The local release script completed unit tests, release lint, and both signed builds successfully.

Android requires an update to have the same signing identity as the installed app. A debug-signed 1.1.5 whose private key is unavailable cannot be updated in place with a different key. Do not clear app storage or uninstall as a signature workaround. Any separately authorized migration must start with a verified current backup and a correctly signed destination build.

## Remaining physical-device validation

The S25 Ultra incident was recovered on the existing 1.1.5 by removing only the explicitly authorized receipt after a verified full backup. No new APK was installed on that phone during this work.

For 1.1.7, still verify long real drives, GPS accuracy/loss and tunnels, OEM battery optimization, reboot/process recovery, actual stop detection and Prodent/Zvezna matching, widget and Quick Settings tile behavior, camera/provider attachments, sharing to real apps, and low-storage/provider errors. The emulator tests do not certify these device-specific behaviors.
