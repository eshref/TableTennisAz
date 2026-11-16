# Android verification

## Doubles ranking — version 0.3.0

The project owner explicitly requested implementation of the confirmed doubles
discovery on 2026-11-16, then requested a new task branch. Work is on
`feat/doubles-rankings`, based on `b6eec08`. The follow-up scope is recorded in
[discovery](android-app-discovery.md#doubles-ranking-follow-up-discovery).

The debug APK is version 0.3.0, version code 3, with the existing Android 8.0
minimum. Artifact: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `784f923bedc3f77bb33a172a4b940d92a84b6181ddcd7528afb0341ab2c6762d`.

APK v2 signature verification passes. Normal production TLS and hostname
verification remain enabled.

### Deterministic checks and review

The complete application integration suite passed **57 cases**, with no failures
or skips, together with Android lint, debug APK assembly, and test APK assembly:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

After replacing an indexed numeric array with a named source-row type during
review, all **13 doubles cases**, lint, and both APK builds passed again:

```sh
./gradlew :app:testDebugUnitTest --tests 'az.tabletennis.Doubles*' :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The existing 44 cases cover the original application behavior; the new cases
exercise distinct doubles guarantees through native Android views, a real HTTP
fixture server, production parsing, and durable storage. Assertions inspect
visible UI state and external HTTP requests.

| Suite | Cases | Distinct doubles guarantees |
| --- | ---: | --- |
| DoublesWorkflowTest | 3 | Published positions at tied points, full names, either-partner search, independent partnerships, interrupted identity caching, incomplete-search progress, other screens during loading, failed new-pair refresh preservation, pair history/profile/match navigation, and long-age offline relaunch |
| DoublesFailureTest | 5 | HTTP failure, missing ranking, partially malformed rows, conflicting pair identities, and conflicting source positions cannot fabricate or replace a complete snapshot |
| DoublesFreshnessTest | 3 | Below/exactly/above 15 minutes, saved rows before delayed refresh, and reuse of saved pair identities |
| DoublesHistoryRefreshTest | 1 | JSON/HTML name-whitespace compatibility, atomic refresh of all loaded pages, retry without losing coverage, and rejection of another partnership sharing a player |
| DoublesNavigationTest | 1 | Exact list offset and search preservation after real pause/resume, activity recreation, and switching between rankings; explicit empty pair history |

Independent Standards and Spec reviews found no hard standards violations and
no confirmed specification gaps. The optional named-source-row advisory was
resolved. Tests run on API 26; the existing individual-ranking workflow also
runs on API 36. Batch sizes remain the website's source pages, and dates do not
establish exact within-day ordering.

### Live compatibility

`LiveDoublesTest` is the separate installed-app check for doubles ranking HTML,
full-name metadata JSON, and pair-history HTML. The opt-in CI `live_site` job
runs it alongside the existing complete application journey. Its first-load
timeout allows for the website's per-pair metadata lookups.

The final APK installs and launches on an Android 8.0 / API 26 x86_64 emulator.
`LiveDoublesTest.liveDoublesShowFullNamesAndOpenTheirPublishedPairHistory`
passed: **OK (1 test)** in 270.192 seconds. It starts without saved data, loads
Players, switches to Doubles, resolves all 848 published pair identities, and
opens a pair's published results. Visible first-load progress was inspected;
the test requires a successful update and readable pair rows before navigation.
No fixed live names, counts, or scores are asserted by the automated test.

The first Gradle connected-test attempt was stopped after a ddmlib property
timeout during slow emulator startup, before application testing began. Both
final APKs were then installed through ADB and the same test was run directly:

```sh
adb shell am instrument -w -r -e class az.tabletennis.LiveDoublesTest az.tabletennis.test/androidx.test.runner.AndroidJUnitRunner
```

The legacy image needed an explicit vendor image and disabled emulator
EncryptUserData feature to boot with the current emulator. Initial System UI
startup dialogs were dismissed before testing. Live HTTPS traverses this
workspace's proxy; its public CA was installed only in the temporary emulator's
system trust store. App TLS verification was not relaxed. A physical phone,
unproxied connection, and current-version Android device were not tested.

### Packaged offline restart

After the live journey, an external ADB/UIAutomator check captured visible
doubles ranking rows, their successful-update time, and the selected pair's
history content, profile-link text, and update time. Wi-Fi and mobile data were
disabled, the app process was force-stopped, and the same screens were opened
in a new process. All captured rows, history content, and update times matched;
refresh was disabled on both offline screens. The check passed and connectivity
was restored afterward. Assertions and polling used fresh native accessibility
trees, with unavailable trees retried rather than reusing an older capture.
No private stored files or internal tables supplied assertion evidence.

The temporary emulator, source downloads, and task tools were removed after
verification. Full-suite XML and verification logs are retained as ignored build
artifacts under `app/build/reports/doubles-implementation/`; the APK remains at
the path above.

## Complete first test APK — issues #2–#9

Implementation of the remaining first-delivery tickets was explicitly requested
on 2026-11-16. Local `main` was fast-forwarded to `ec4a730` before creating
`feat/2-9-complete-first-apk`. The installable artifact is version 0.2.0 (version
code 2), built with Linux, JDK 21, Gradle 9.1.0, AGP 9.0.1, SDK 36 and Build Tools
36.0.0. Minimum Android version remains 8.0 / API 26.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `87cdf27736ca39bdfd5d5667c1052e4091b14f963ea39048d7e8e57d8f10b501`.

APK signature verification passes with v2 signing and minimum API 26. This is a
debug-signed test APK, not a Google Play release. Installation and phone-testing
instructions, source batching limits, offline behavior, and scope limitations
are in the root README.

### Deterministic application integration coverage

Final verification after review cleanup passed **44 cases**, Android lint, debug
APK assembly and connected-test APK assembly:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

| Suite | Cases | Distinct guarantees |
| --- | ---: | --- |
| RankingsWorkflowTest | 2 | Combined identity/ranking/search interaction and long-age offline relaunch on API 26 and API 36 |
| RankingsFailureTest | 4 | HTTP, missing markup, partial rows and conflicting category identities cannot fabricate or replace a complete ranking |
| RankingsFreshnessTest | 3 | Below/exactly/above 15 minutes; saved rows precede delayed refresh |
| ProfileWorkflowTest | 2 | Search-to-profile, singles/doubles history, same-named source identities, back context and offline snapshots |
| MatchJourneyTest | 1 | History-to-doubles-to-each-participant navigation and offline details/context |
| RatingHistoryTest | 2 | Source points, source order and yearless labels, accessible values, floating-point normalization, offline reuse and explicit absent history |
| ResultsWorkflowTest | 1 | Duel source/date ordering, singles/doubles cards, real decorative image links, details/profile navigation and offline feed |
| BatchedResultsTest | 1 | Bounded mixed batches, cup/league provenance, overlap deduplication, saved cursors, multi-page events and atomic failure/retry of an incremental batch |
| ResultsFilterTest | 2 | Source-specific type/event selections, events outside the general batch, selected-event pagination/refresh, offline isolation and stale-response rejection |
| PlayerHistoryPagingTest | 1 | Multiple list containers, individual team submatches, source-ID verification for identical names, further history pages and verified details offline |
| ScreenFreshnessTest | 12 | All four new screen types at each freshness boundary, immediate cached display, delayed responses and correct update times |
| SavedScreenFailureTest | 13 | First-load failure, manual recovery at a fresh age, preservation on HTTP/markup failure, year-old offline reuse, wrong identity, incomplete doubles, bad chart values, unlinked rows and oversized IDs |

These tests use native Android views, real HTTP fixture responses, production
HTML/JSON parsing, and private-file persistence. Assertions inspect public UI
outputs and captured HTTP requests, not internal files or tables. New feature
scenarios run on API 26; the existing rankings workflow also runs on API 36.

### Installed app and live source journey

The APK installs and launches on an Android 8.0.0 (API 26, x86_64) emulator.
`LiveExperienceTest.rankingsProfileChartAndResultsMatchParticipantUseTheLiveAndroidClient`
passed: **OK (1 test)**, approximately 202 seconds. It starts with empty saved
data, retrieves both ranking categories, opens the selected player's profile and
points chart, then follows mixed Results to match details and a participant's
profile. It asserts visible successful-update information and readable UI data,
without hardcoded live player names, counts or scores.

Read-only live verification exposed source variants absent from the initial
samples: decorative profile links over singles-player images, multiple history
containers, and individual games nested inside team fixtures. Corresponding
fixture-backed regressions now pass. Team-history candidate names are followed
by participant-ID verification before attribution; verified match details are
saved. Player histories retain pagination and loaded pages through refresh.

The opt-in GitHub workflow runs this complete journey. The earlier
`LiveRankingsTest` remains available as a narrower smoke command; the complete
journey contains its first-load ranking guarantee.

### Packaged offline restart

The final post-review APK (checksum above) was installed on the Android 8.0.0
emulator and freshly loaded through its native UI using the production website
configuration. An external ADB/UIAutomator driver recorded the rankings row,
profile name/points, accessible chart labels, first Results row, match score and
participants, participant profile, and each screen's successful-update label.

Wi-Fi and mobile data were then disabled. The app was force-stopped and launched
with a cleared activity task, creating a new process and new screen instances.
The same native navigation reopened all six saved screens. Every recorded
customer-visible value and update time matched the online capture exactly;
refresh was disabled offline. The check passed. Network services were restored
afterward. Assertions and polling used native UI/accessibility outputs; private
files were cleared only to prepare the fresh online visit, never used as
assertion evidence.

The temporary emulator and downloaded source captures were removed after
verification. The deliverable APK remains at the path above.

### Review and compatibility limits

Independent standards/specification reviews found no hard standards violations
or confirmed feature defects in issues #2–#8. Both standards advisories were
resolved: shared website HTTP policy and shared incremental-refresh timestamps.
Ticket #9's evidence is recorded here rather than inferred from host tests.

There is no hardware acceleration in this environment. The software emulator
needed a small temporary AVD, explicit vendor image, and an emulator feature
setting avoiding the legacy image's vendor-disk ordering conflict. Initial
system ANR dialogs were dismissed after startup settled. Connected assertion
polling now reads the visible label until the final assertion, avoiding an error
screenshot on every poll.

Live traffic traverses this workspace's HTTPS proxy. Its public CA was installed
only in the temporary emulator system trust store. The app retains normal TLS
certificate and hostname verification and disallows cleartext production
traffic. An unproxied connection and the owner's physical phone were not tested.
A current Android emulator/device was unavailable; API 36 evidence is the
Robolectric rankings workflow, not current-device installation or full feature
verification on that version.

Results coverage is partial: the chooser has no verified global date order,
and loading a bounded batch cannot establish globally latest results. Website
markup or endpoint changes can require another APK. Google Play publication and
production signing remain outside this delivery.

## Earlier rankings-only verification — issue #1

Implementation of [issue #1](https://github.com/eshref/TableTennisAz/issues/1)
was explicitly requested on 2026-11-15. Later first-delivery tickets remain outside
this change. Build environment: Linux, JDK 21, Gradle 9.1.0, AGP 9.0.1,
Android SDK 36 / Build Tools 36.0.0. Minimum runtime: API 26.

### Deterministic application integration scenarios

The primary test seam is the native UI plus a real local HTTP fixture server.
Both scenarios use production HTTP requests, HTML parsing, combination and file
persistence. No database tables, private methods or test helpers are assertion
subjects. Android test dependencies are JUnit/Robolectric and AssertJ; the .NET
xUnit/FluentAssertions instructions do not apply to this Java application.

- `RankingsWorkflowTest.combinedRankingsSearchAndOfflineRelaunch` runs on API 26
  and API 36. It combines both source categories, excludes sidebar entries,
  deduplicates a repeated identity, shows points and ranks 1/2/2/4, searches
  Azerbaijani dotted and dotless I, and preserves combined ranks during search.
  It then destroys the Activity and repository, stops the website, advances the
  application clock by a year and reopens the screen. All saved rankings and the
  same successful-update label remain visible.
- `RankingsFailureTest.unavailableFirstVisitAndFailedRefreshNeverInventOrReplaceSavedRankings`
  runs on API 26 with four data rows: HTTP 403, missing rankings markup, a list
  with one valid and one malformed row, and contradictory points for a repeated
  source identity. Each row checks a first visit without saved data, recovery
  through manual refresh, preservation after another failure, and saved-data
  readability after reopening offline. Partial category data is never shown as
  a complete combined snapshot, and failures never manufacture an update time.

The workflow test first failed against the loading-only Activity, then passed
with the implemented ranking workflow. Host-side simulation cannot establish
real Android TLS compatibility or device installation on its own.

Final deterministic verification passed: six cases, Android lint (zero errors),
debug APK assembly and connected-test APK assembly. The standards/specification
reviews found no outstanding issues after assertion polling was moved into the
Assert phases.

### APK and live source verification

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` and
passes `apksigner verify` (v2 signing, supporting the minimum API level).

`LiveRankingsTest.bothLiveCategoriesProduceReadableRankings` is the separate
connected-device check. It starts without saved rankings, opens the native app
and requires a successful-update label and rendered ranking rows. A successful
snapshot requires both live categories to pass the production client and parser;
no fixed live names, player counts or scores are asserted.

The HTTP client requests HTML explicitly, supplies Azerbaijani language
preferences and a browser-compatible user-agent identifying TableTennisAz.
HTTPS uses normal platform certificate and hostname verification; the app does
not disable TLS checks or permit cleartext production traffic.

On 2026-11-16, the APK installed and launched successfully on an Android 8.0
(API 26, x86_64) emulator. The connected live UI test passed: `OK (1 test)`.
Both live categories passed the production HTTPS requests and parsing, and the
native UI displayed ranking rows plus a successful-update time. The complete
connected test took about 106 seconds on this software-emulated device.

This Linux environment has no `/dev/kvm`. The first connected attempt was
blocked by an Android system-process ANR dialog and lost window focus. After
initial startup settled, the dialog was dismissed and emulator animations were
disabled; the retry passed. The sandbox's public proxy CA was installed only in
the emulator system trust store so its HTTPS proxy could be used. No app TLS
configuration was relaxed. API 36 was exercised under Robolectric; an API 36
emulator/device was not available for this run.

### Reproduction

See the root README for build, fixture-test and connected-test commands. CI runs
the deterministic checks and publishes a debug APK artifact. A manually selected
workflow input runs the live check separately because website availability is
outside the app's control.
