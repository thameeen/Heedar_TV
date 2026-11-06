# Heedar TV

An Android IPTV client for Xtream Codes panels: live channels, VOD and series,
built for both touch devices and Android TV.

> **Status:** the P0–P6 refactor described below is implemented. The Gradle build
> itself has **not** been executed in this environment (AppJet cannot run the
> Android toolchain); it is verified by GitHub Actions and by
> `scripts/check_project.py`. See [Verification](#verification).

## Quick start

```bash
# Unit tests only (fast, no device needed)
gradle testDebugUnitTest

# Debug APK
gradle assembleDebug
```

The project ships without a Gradle wrapper JAR. Either run the system `gradle`
(8.7+) as above, or generate the wrapper once with `gradle wrapper --gradle-version 8.7`
and commit `gradle/wrapper/`. CI uses `gradle/actions/setup-gradle` and does not
need the wrapper.

## Architecture

```
com.heedar.tv
├── HeedarApp.kt              Application; owns AppContainer
├── di/AppContainer.kt        Manual DI (no Hilt/kapt)
├── domain/                   Pure Kotlin: models, XtreamResult
├── data/
│   ├── remote/               DTOs, Retrofit service, URL normaliser, client factory
│   ├── XtreamRepository.kt   Interface the ViewModels depend on
│   ├── XtreamRepositoryImpl  Network → domain mapping, failure typing
│   ├── EncryptedPlaylistStore  AES-GCM via Android Keystore
│   └── StreamUrlBuilder.kt   Pure URL construction
└── ui/
    ├── login/                LoginActivity + LoginViewModel + LoginUiState
    ├── channels/             ChannelsActivity + ViewModel + ListAdapter
    └── player/               PlayerActivity (media3 ExoPlayer)
```

Dependency direction is one-way: `ui → data → domain`. The domain layer has no
Android or Gson imports, so it is fully unit testable on the JVM.

## Decisions taken

The five open questions were answered with these defaults; each is easy to reverse.

| Question | Decision | Why |
| --- | --- | --- |
| Phone/tablet, TV, or both? | **Both.** `leanback` + `LEANBACK_LAUNCHER` declared `required="false"`, `touchscreen` not required | One APK; D-pad focus states are styled in `bg_channel_item.xml` |
| minSdk | **23** (target/compile 34) | `KeyGenParameterSpec` for the AES Keystore key requires API 23 |
| Cleartext HTTP | **Allowed**, with an explicit in-app warning | Xtream panels are commonly plain HTTP on a LAN; blocking it would break the main use case |
| Test data | **MockWebServer fixtures** | No real credentials were provided; wire assumptions are still verified |
| Extra libraries | **None beyond Retrofit/OkHttp/Gson/Coil/media3/coroutines** | No Hilt, no Navigation, no Room, no annotation processing — fewer moving parts and no kapt/KSP in the build |

## What the refactor changed

### P0 — the build did not exist
The repository had Kotlin sources but no Gradle files, no manifest at the module
path and no resources, so nothing could compile. Added `settings.gradle.kts`,
root + `app` build scripts, a version catalog, `gradle.properties`,
`.gitignore` and a single canonical `app/src/main/AndroidManifest.xml`.

### P1 — data layer
* **`UrlNormalizer`** turns user input (`192.168.1.10:8080`, a full
  `.../player_api.php` URL, trailing slashes, junk) into a Retrofit-legal base URL
  instead of concatenating strings at call sites.
* **DTOs are all-nullable** because real panels omit keys and mix numeric/string
  types. A mapper decides validity and drops unusable rows, so one malformed
  channel no longer kills the whole list.
* **`XtreamResult`** replaces exceptions-as-control-flow: `NETWORK`, `AUTH`,
  `PARSE`, `UNKNOWN`. The repository never throws.

### P2 — MVVM
Login and Channels are driven by `StateFlow<UiState>` collected with
`repeatOnLifecycle`, so rotation and process death no longer lose or duplicate
work, and every screen state (loading / content / empty / error) is explicit.

### P3 — player lifecycle
The original player created an `ExoPlayer` and never released it, and rebuilt
state in `onCreate` only. Now the player is created in `onStart`, released in
`onStop`, and playback position is saved/restored. `newIntent(...)` is typed
instead of relying on string extras.

### P4 — UI
Hardcoded strings moved to `values/strings.xml` (Arabic, the default) and
`values-en/strings.xml`, so RTL and translation work correctly. The channel list
uses `ListAdapter` + `DiffUtil` (comparison logic extracted to
`ChannelDiffLogic` for JVM tests) and Coil for logos with a placeholder.

### P5 — security
* The playlist password is encrypted with an AES-GCM key held in the Android
  Keystore (`EncryptedPlaylistStore`); no plaintext credentials on disk.
* `allowBackup="false"` so the encrypted credential blob is never shipped
  off-device by Android's auto-backup.
* `network_security_config.xml` makes the cleartext decision explicit and
  documented in-file rather than implicit.

### P6 — CI
The workflow lived in a directory literally named `.github/workflows ` (trailing
space), so GitHub never ran it. It is now at
`.github/workflows/android_build.yml` and runs `testDebugUnitTest` +
`assembleDebug`.

## Tradeoffs

* **Manual DI over Hilt.** Loses compile-time graph validation and adds a little
  wiring in `AppContainer`, but removes kapt/KSP, which is the most common cause
  of Android build breakage and slows every build.
* **Repository returns `XtreamResult` rather than throwing/`Result<T>`.** More
  explicit failure semantics, at the cost of a custom type and mapping code.
  `Result<T>` would have been less code but conflates "panel said no" with
  "network died".
* **Cleartext HTTP allowed.** Necessary for LAN panels, but it means credentials
  can travel unencrypted; the UI warns when an `http://` URL is typed. If all
  target panels are HTTPS, flip `cleartextTrafficPermitted` to `false` in
  `network_security_config.xml`.
* **Single playlist.** `PlaylistStore` holds one panel. Multi-panel support would
  mean a list plus a selector — deliberately out of scope.
* **No pagination/caching.** Live channels are fetched once per screen open. A
  panel with tens of thousands of channels will want paging + a local cache
  (Room), which this refactor intentionally does not add.
* **`useUnconfinedTestDispatcher` not used**; tests use `StandardTestDispatcher`
  so ordering is explicit rather than accidental.

## Verification

| Check | Command | Status |
| --- | --- | --- |
| Resource/id/manifest wiring | `python3 scripts/check_project.py` | **passes** (20 strings, 4 layouts, 3 drawables, 17 ids) |
| Workflow YAML parses | `ruby -ryaml -e 'YAML.load_file(".github/workflows/android_build.yml")'` | **passes** |
| JVM unit tests | `gradle testDebugUnitTest` | requires the Android SDK — runs in CI |
| Debug APK | `gradle assembleDebug` | requires the Android SDK — runs in CI |
| Dependency advisories | `run_tests(security)` | **incomplete** — Gradle has no resolved lockfile until a build runs, so the scanner had 0 packages. Not a pass. See [Dependency security](#dependency-security). |
| Dependency advisories, transitive | `python3 scripts/check_dependencies.py` | **passes** — 75 modules resolved from published POMs, 0 advisories after the guava constraint. 5 version-less artifacts remain unscanned. |

`scripts/check_project.py` verifies what a Gradle build would catch first:
every `R.string`/`R.layout`/`R.drawable` reference resolves, every view-binding
property used in Kotlin is an actual id in the corresponding layout, every
package declaration matches its directory, and every class named in the manifest
exists. It is a smoke check, **not** a substitute for compiling.

## Dependency security

The shared scanner resolves *lockfiles*, and a Gradle project has no
`gradle.lockfile` until a build runs — which needs the Android SDK, absent here.
So the scanner reports **0 packages inspected**, `status: incomplete`. That is
not a clean scan and is never described as one.

**Substitute used instead.** `scripts/check_dependencies.py` walks the real
dependency graph from *published* POM metadata — Maven Central and Google Maven
— rather than guessing versions. It reads the 16 direct coordinates in
`gradle/libs.versions.toml`, follows `<dependencies>` transitively with property
interpolation, honours BOM imports (`scope=import`), inherits
`dependencyManagement` up the parent chain, skips `test`/`provided`/`optional`
scopes, normalises exact pins (`[2.5.1]` → `2.5.1`), applies highest-version-wins
per module, records which direct dependency introduced each module, then sends
every resolved coordinate to the OSV `querybatch` API and enriches each hit via
`/v1/vulns/{id}` for real severity and fix versions. It currently resolves
**75 modules** from 16 direct coordinates.

**Finding, and what was done about it.** `com.google.guava:guava:33.0.0-android`
is reached transitively through `androidx.media3:media3-exoplayer 1.4.1`, so it
ships inside the APK — this is not a build-time-only issue.
`GHSA-xxph-c9ww-hj94` / `CVE-2026-102554` (HIGH, GitHub-reviewed, CWE-400/502/770):
`CompactHashMap`/`CompactHashSet`/`MapMakerInternalMap` `readObject` allocate an
attacker-controlled `elementCount` before validating the stream, so a ~72-byte
payload can exhaust memory. Fixed in **33.7.2**.

Remediation is a `constraints` block in `app/build.gradle.kts` pinning
`com.google.guava:guava:33.7.2-android`. Why a constraint and not a media3 bump:
every media3 release from 1.4.1 through 1.11.1 still pins guava `33.3.1-android`,
which is *below* the fix, so upgrading the player would not have remediated it —
and a 1.4.1 → 1.11.1 jump changes player APIs that cannot be compiled or tested
here. Guava is binary-compatible across 33.x, so the constraint is the smallest
verifiable change. Remove it once media3 depends on guava ≥ 33.7.2.

After the constraint the resolver reports `33.0.0-android → 33.7.2-android` and
**no advisories across the 75 resolved coordinates**.

**What is still not covered** — stated rather than glossed over:

* Five artifacts appear in POMs without a version and with no reachable BOM
  supplying one, so they cannot be scanned: `com.google.code.findbugs:jsr305`,
  `com.google.errorprone:error_prone_annotations`,
  `com.google.j2objc:j2objc-annotations`, `org.checkerframework:checker-qual`
  and `com.squareup.okio:okio` (a version-less entry; `okio` itself still
  resolves to `3.6.0` via okhttp). They are compile-time annotations/helpers.
  The script reports them and exits `2` — it never silently drops them.
* The POM walk is an *approximation* of Gradle's resolver: no variant-aware
  selection, no `resolutionStrategy`/platform alignment, no attribute matching.
  A coordinate Gradle resolves differently would be missed.
* A passing run is not a certification of the source, and the advisory snapshot
  is a point in time.

**Accepted risk, unchanged**: `GHSA-r937-wjx7-w2jp` (`CVE-2026-53914`), "unsafe
deserialization in the Kotlin build cache enables code execution", MODERATE,
vector `AV:L/AC:H/PR:H`, affecting `kotlin-gradle-plugin`; the only fixed release
is `2.4.20-Beta1`. It is deliberately not patched: the fix is a pre-release and a
1.9 → 2.4 major jump, outside the Kotlin range AGP 8.5.2 supports, and cannot be
compiled or verified here. The plugin is build-time only, the shipped
`kotlin-stdlib 1.9.24` is clean, and the CVSS vector requires local access plus a
poisoned build cache. Revisit once a stable Kotlin 2.x that AGP supports exists.

**To get authoritative coverage**: enable Gradle dependency locking, let CI
commit the generated `gradle.lockfile`, and re-run `run_tests(security)` against
it. That replaces the approximation above with Gradle's own resolution.

### Test coverage

* `UrlNormalizerTest` — scheme, port, panel-path and junk input handling.
* `StreamUrlBuilderTest` — live/VOD URL shape, no double slashes.
* `XtreamRepositoryImplTest` — real Retrofit/OkHttp/Gson stack against
  MockWebServer: DTO mapping, dropped malformed rows, blank-logo normalisation,
  auth success/rejection, HTTP 500 → `NETWORK`, object-instead-of-array → `PARSE`,
  refused connection → `NETWORK`.
* `LoginViewModelTest` — validation, cleartext detection, auth outcomes,
  one-shot navigation event.
* `ChannelsViewModelTest` — loading → content / empty / error, retry.
* `ChannelDiffLogicTest` — identity vs content comparison rules.

## Known limitations

* Not compiled or run on a device in this environment; native Android builds are
  out of scope for AppJet. First-run verification is GitHub Actions or a local
  `gradle assembleDebug`.
* Only the live-channel screen is wired end to end. `loadVod()` / `loadSeries()`
  exist in the repository and are unit-testable, but no VOD/series screens were
  built — the original code had none either.
* No EPG, favourites, search or parental controls.
