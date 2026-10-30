# URLCheck local changes

Last updated: October 30, 2026, Korea Standard Time.

[한국어](LOCAL_CHANGES_KR.md)

Compared with upstream URLCheck, this fork changes log storage and rule/icon reuse, and reduces work before repeated popup display. To reduce work before popup display, the fork uses asynchronous file logging, caches rules, regular expressions, Hosts lookups and app icons, defers secondary app menu creation, and removes repeated work from preference-history sorting. Fixed-browser selection and screen orientation were also added while retaining the latest upstream features.

## Source baseline and integration

| Item | Value |
|---|---|
| Working repository | `C:/Users/enLioN/Personal_Project/URLCheck` |
| User fork | https://github.com/jprapstyle/URLCheck |
| Upstream | https://github.com/TrianguloY/URLCheck |
| Initial baseline | `0a4958d945ca0b86a169694dde20e53d0bbb2691` |
| Integration baseline | `5886dad266f8d685ad2c4537bd36225185881a9b` |

The fork and upstream master matched at integration. Seven commits affecting 17 files had been added since the initial baseline, including popup positioning and Redirect. Both were preserved. Redirect is disabled by default and supports host replacement, manual/automatic application, and JSON/visual editors.

Twelve modified files and five added source/test files were ported from the previous local copy. `ClearUrlCatalog.java` was merged using a three-way merge; English `strings.xml` was reconciled by resource key to preserve both sets of changes. Orientation and additional warm-launch optimizations followed. Git history records the uploaded source and documentation revisions.

## Logging and caches

Instead of rewriting an expanding SharedPreferences string, logs are appended to internal `url-log.txt`. A single worker serializes operations to reduce UI-thread file work and large-string reconstruction.

- Legacy logs are migrated with `AtomicFile` before the old preference key is removed.
- Previews are limited to 64KiB, with safeguards against replacing the full log with a truncated preview.
- Logging, clearing, backup, and restore share a lock.
- Backup and restore treat log files as sensitive data.
- Logs remain local; no upload feature was added.

Main files: `LogStore.java`, `LogModule.java`, `BackupActivity.java`.

| Cache | Behavior |
|---|---|
| ClearURL rules | Reuse parsed rules; invalidate after rule changes |
| ClearURL regular expressions | Up to 512 LRU entries |
| Hosts lookups | Up to 16 LRU entries |
| App icons | Up to 32 Drawable ConstantState LRU entries |

Main files: `ClearUrlCatalog.java`, `ClearUrlModule.java`, `Hosts.java`, `IntentApp.java`.

## Browser selection

Fixed-browser settings were added to the opening/sharing module. Long-press the popup Open button to pin the current browser, choose another, or clear the fixed choice. English and Korean labels are available.

Without learned history, a link-specific app takes priority, followed by the fixed browser. Existing `LastOpened` history takes precedence even with fixed-browser selection enabled. For example, choosing Choose Browser for a Coupang link influences subsequent selection.

Existing preference keys and pairwise scores from -3 to +3 are preserved; there is no timer-based expiration. Domain-scoped sorting and filters for hidden, referring, and rejected apps remain active. Pinning does not itself launch an app.

Main files: `BrowserPreference.java`, `OpenModule.java`, `IntentApp.java`, `config_open.xml`, and English/Korean `strings.xml`.

## Screen orientation

An orientation selector appears near theme and popup-size settings. Its label and spinner share a row like the theme control, with explanatory text underneath.

| Option | Behavior | Android request |
|---|---|---|
| Follow system settings | Honors system auto-rotation/rotation lock | `SCREEN_ORIENTATION_USER` |
| Portrait only | Default; requests portrait | `SCREEN_ORIENTATION_PORTRAIT` |
| Landscape only | Requests landscape | `SCREEN_ORIENTATION_LANDSCAPE` |
| Always auto-rotate | Uses the sensor regardless of system rotation lock | `SCREEN_ORIENTATION_FULL_SENSOR` |

The choice is stored in `screen_orientation`. Activity creation/resume callbacks in `URLCheckApplication` apply it across the popup and app screens. Previous per-Activity portrait declarations were removed. English and Korean are supported.

Android/manufacturer policies may override fixed-orientation requests on large screens. Folded/unfolded behavior and all four options need dedicated device verification.

Main files: `URLCheckApplication.java`, `OrientationSettings.java`, `SettingsActivity.java`, `AndroidManifest.xml`, `activity_settings.xml`, and English/Korean `strings.xml`.

## Warm launch optimizations

Popup speed while the process is already running is the priority; full cold-start optimization is deferred.

### Deferred secondary menu creation

Previously, secondary app labels and menu entries were prepared whenever the popup opened. The selected app now appears first, and the secondary menu is built only when the user opens the alternative-app list. Candidate resolution and existing filters still run. No long-lived app-resolution cache was added.

Changed methods: `OpenModule.java` — `updateSpinner()` and `showList()`.

### Reduced sorting overhead

Domain parsing and SharedPreferences lookup now occur once per sort instead of per comparison. Component names are reused during sorting. Preference keys, stable ordering, domain scope, and score interpretation are preserved.

Changed method: `LastOpened.java` — `sort()`.

### Animations

The existing animation option controls `LayoutTransition`. Disabling it can avoid layout-change animations and initialization costs, but does not skip process creation or URL analysis. User animation settings and behavior were preserved. ON/OFF performance was not compared.

## Build and validation

| Check | Result |
|---|---|
| Release APK | `3.5-enlion.1`, successful, unsigned |
| Alpha APK | `3.5-enlion.1-alpha.1`, successful |
| Evaluation app and AndroidTest APK | Successful builds |
| Browser priority checks on Fold5 | 40 passed |
| Latest Release and Alpha Lint | Each: 0 errors, 133 warnings |
| Lint immediately after integration | 0 errors, 153 warnings |
| Fold5 installation | Warm-launch changes and aligned orientation UI installed |

Removing fixed-orientation declarations removed 20 related warnings. Other warnings were not broadly fixed or suppressed. The 40 checks cover priority, learned history, domain scope, fallback, persistence, and English/Korean settings, not every app feature. Tests ran in a separate Evaluation package; temporary packages were removed afterward.

Measurements used `com.trianguloy.urlchecker.alpha`, `3.5-ALPHA-browser2`, versionCode 47. Versioning below was applied afterward; the newly numbered build has not been installed.

### Fork versioning

Releases use `upstreamVersion-enlion.revision`. Follow the integrated upstream version and increment the fork revision per release. After integrating 3.6, start at `3.6-enlion.1`. Increase `versionCode` for every distributed update of a package; never reset it when the upstream version changes.

| Build | Version | versionCode | Package |
|---|---|---|---|
| release | `3.5-enlion.1` | 48 | `com.trianguloy.urlchecker` |
| alpha | `3.5-enlion.1-alpha.1` | 48 | `com.trianguloy.urlchecker.alpha` |
| evaluation | `3.5-enlion.1-test.1` | 48 | `com.trianguloy.urlchecker.test` |

These are different packages, so code 48 is used for each. A subsequent Alpha distribution could use `3.5-enlion.1-alpha.2` and code 49. Keeping the Alpha package name permits updates when signed with the same key.

Release enables minification and resource shrinking. No release keystore/password is configured, so its APK is unsigned and needs signing before installation/distribution. A matching upstream package name does not allow an update with a different certificate. No release key or GitHub Release was created.

APK paths:

- Release: `app/build/outputs/apk/release/URLCheck-3.5-enlion.1.apk` (unsigned)
- Alpha: `app/build/outputs/apk/alpha/URLCheck-3.5-enlion.1-alpha.1-alpha.apk`

Benchmarks predate the version-name change. Renaming a build is not a new performance measurement.

PowerShell build commands:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease lintRelease assembleAlpha lintAlpha --offline --console=plain
.\gradlew.bat assembleEvaluation assembleEvaluationAndroidTest '-PcheckVariant=evaluation' '-PcheckRunner=com.trianguloy.urlchecker.BrowserChecks' --offline --console=plain
```

Use `--offline` with dependencies/SDK components already available. Local `local.properties` supplies the SDK path.

## Fold5 performance measurements

### Upstream versus modified app

Upstream rewrites accumulated logs in SharedPreferences; the modified app uses asynchronous file appends and caches. Upstream builds the secondary app menu during popup setup; the modified app defers it until the list opens. These reduce work before display, but an actual speed difference requires matched upstream/modified measurements.

All results below are local modified builds, not an unmodified upstream baseline. The 124–138ms versus 123ms comparison concerns additional optimization within modified builds.

### Modified app results

Device: Galaxy Z Fold5 SM-F946N. External links came from Orak, `com.skplanet.ocb.locker`. Values are Android `ActivityTaskManager` `Displayed` times, not physical tap-to-visible latency or completion of all URL analysis.

| Session | Display times | Summary |
|---|---|---|
| Modified app before additional optimization | 235, 135, 119, 128, 115ms | Overall mean 146.4ms; attempts 2–5 mean 124.25ms |
| After force-stop | 731, 117, 156, 114, 166ms | Cold 731ms; subsequent mean 138.25ms |
| After warm-launch optimization | 140, 115, 100, 138ms | Mean 123.25ms; range 100–140ms |

A new process was confirmed for the first link after force-stop. Cold-start 731ms is one sample. Five optimized attempts were requested, but only four launch/display pairs followed the start marker; only four were counted. Preparation launches were excluded.

The optimized mean is similar to or slightly faster than earlier warm means. Sample sizes and variation do not establish a clear gain. No crash, ANR, or OutOfMemoryError logs appeared in these windows; the optimized window had no process restart.


## Retesting procedure

1. Install the intended build; keep animations, orientation, modules, and fold state consistent.
2. Open/close the same link once from the same source app for preparation.
3. Record a start marker.
4. Repeat link opening and Back dismissal at roughly three-second intervals at least five times, without force-stop.
5. Record an end marker and verify matching launch/display counts.
6. Separate process-restart runs; compare means, medians, and ranges.
7. Verify alternative-app selection, fixed browser, and learned history.

Measure cold starts separately after force-stop; do not mix them into warm results.

## Follow up work

- Measure module initialization, app resolution, and layout creation separately.
- Increase matched samples and obtain an unmodified upstream baseline.
- Verify all orientation choices and URL/screen-state retention through folding, unfolding, and rotation.
- Manually verify the deferred alternative-app menu.
- Address cold-start optimization after warm-launch verification.

See [CONTRIBUTING.md](CONTRIBUTING.md) for development/translation guidance and [PRIVACY POLICY.md](PRIVACY%20POLICY.md) for privacy information. This record does not replace the existing policy.
