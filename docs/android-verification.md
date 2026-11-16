# Android verification

## Comfortable Players ranking and cached portraits — issue #25

Implemented on `feat/25-comfortable-player-photos`, based on `2b9677b`.
Players rows now group a wrapping full name and current points beside an actual
circular portrait, with an independent amateur-rank badge. The source ranking's
optional image URL is saved compatibly; snapshots without it, including null or
unusable optional metadata, remain readable. Amateur membership, competition
ties, Azerbaijani search, profile identity/navigation, per-mode scroll context,
and the existing doubles rows retain their established behavior.

Portrait downloads and decoding run independently of ranking work. Serialized
image storage and a bounded memory cache retain the last usable portrait per
player, including offline use after memory loss. Failed downloads cannot change
ranking data or its successful-update time. Binding tokens protect recycled
views, and older URL responses cannot replace a newer saved portrait. Missing,
default, invalid, and failed photos fall back to a bundled silhouette. Redirects
retain the website origin and reject its default placeholder. Normal TLS and
certificate validation remain enabled. No per-player profile crawl or invented
thumbnail filenames were added.

The final full application integration suite passed **100 cases**, with zero
failures, errors, or skips. It includes the existing API 26/36 rankings and
navigation workflows and new native-UI/real-HTTP photo scenarios. Coverage adds
independent failures, placeholder redirects, delayed row reuse/mode changes,
Activity recreation and selected-profile navigation, overlapping old/new image
URLs, durable offline reuse, and legacy optional metadata. Assertions inspect
rendered images/text/accessibility and external HTTP requests. Saved files are
used only for setup. Final portrait-rendering corrections passed all nine
affected photo/layout cases, lint, and both APK builds again:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Native comparison exposed software-renderer outline clipping that left square
avatars and a center crop that cut off faces. Explicit antialiased circular
drawing with the reference's upper crop corrects portraits; the silhouette
also stays inside its circle. Native-graphics product-view assertions verify
portrait corner transparency, image identity, and fallback boundaries. Layout
scenarios inspect long names, 150% text, accessible row values, 48dp minimum
targets, both themes, and narrow/landscape widths. A separate native landscape
capture at 150% text exposed fixed controls leaving less space than a complete
row needed. On short screens, mode and search controls now share the list's
scrolling header. The layout scenarios scroll to a row and verify that its full
height fits the viewport; portrait control placement is preserved.

Independent Standards and Spec reviews have no outstanding findings. The
specification review identified concurrent image-file access, directory creation,
and placeholder redirects; all were corrected and re-reviewed. The native
comparison's rendering corrections and test phase organization were also
reviewed.

The debug APK remains at `app/build/outputs/apk/debug/app-debug.apk` and passes
signature verification. SHA-256:
`b43eaa3365f886957647fd2c18ca4de1227b068e1cf37001556620ae358c2e1e`.

### Installed application evidence

The final APK was installed on an Android 8.0 / API 26 x86_64 software emulator.
`PlayerRankingDeviceTest` passed in both system themes: **OK (1 test)** per run.
It compares the visible portraits with known public source images, checks
separate names/points/ranks, searches a long full name, and retains its query
through Activity recreation. Wi-Fi and mobile data were disabled. The dark run
uses the same saved fixtures after a forced process stop, confirming disk-backed
portrait reuse and the unchanged ranking update time in the native UI.

Native captures cover 320 × 640 portrait and 640 × 320 landscape at 160 dpi,
including 150% text in both themes. The corrected landscape captures retain the
entire wrapping name, points, and rank within the viewport after scrolling the
controls away. External ADB/UIAutomator checks additionally verified that the
landscape header accepts search input and switches modes while retaining the
Players query. The native accessibility trees and images record these outcomes.

Screenshots were compared with the approved Comfortable rows reference in
`docs/prototypes/android-final-ui-prototype.html`, including the same İlqar
portrait. Evidence is under the ignored `app/build/reports/issue25/` directory:
`screenshots/issue25/`, native run logs, native accessibility trees, the approved
reference, full-suite XML, rendering checks, and lint output. Source portraits
are bundled only in the test APK for native fixture setup; production retrieves
optional ranking thumbnails. Acquisition and network failures are verified
through the real-HTTP integration suite, rather than inferred from saved fixtures.

The disposable emulator required a task-owned ramdisk without forced encryption,
an Android runtime restart, and waiting through legacy System UI ANR dialogs.
These were environment workarounds; application code and TLS policy were not
relaxed. This was fixture-backed native verification, not a new live-site or
physical-phone journey. API 36 evidence remains host integration; a current
Android device was not available. Temporary emulator data and task tools were
removed after verification.


## Drawer and main-screen layout — issue #24

Implemented on `feat/24-drawer-main-screen-layout`, based on `5c8f37a`.
Rankings and Results now share a compact native title/action bar and a modal
navigation drawer. The active destination is checked; close, backdrop, and Back
dismiss the drawer. API 33+ uses the native back-invoked callback, while older
Android versions retain Activity Back handling. Returning from Results clears
that destination above the existing Rankings Activity. Rankings preserves both
mode-specific queries and list positions; Results preserves each selection's
scroll context through recreation and destination switching.

The additive native-View presentation helpers provide the approved green system
light/dark palette, scalable labels, 48dp actions, segmented selections, touch
feedback, and accessible labels. Main-screen feedback distinguishes loading,
retryable first failure, failed refresh with saved data, offline saved content,
search misses, verified empty Results, and successful refresh. Losing connectivity
before pressing refresh updates the notice without losing content or scroll.
The source/storage policy remains unchanged, including saved-data-first display,
the strict more-than-15-minute threshold, complete atomic saves, unchanged update
times after failures, and independent doubles identity loading. Existing chart
colors use theme resources so unmigrated details remain readable in dark mode.
Ranking photos/row composition and Results cards belong to later tickets.

`MainNavigationTest` adds the drawer/destination lifecycle at API 26 and 36,
including drawer recreation, independent Players/Doubles queries, scrolled
rankings, Results event selection and loaded pages, Results scroll restoration,
clear search, and refresh after losing connectivity. Existing freshness, failure,
source parsing, movement, profile/chart, and match workflows are reused. Assertions
inspect native UI and the external HTTP fixture integration, not private storage.
The installed `MainNavigationDeviceTest` separately checks Android's real task
stack, Back dismissal, retained selection/search, and old detail-screen navigation.
Internal saved fixtures are used only for device setup.

### Verification

The complete native-UI/HTTP-fixture suite passed **80 cases**, with zero failures,
errors, or skips. Android lint and both debug APK builds pass. The final
Back/focus checks also pass at API 26 and API 36. The final responsive-filter
adjustment passed both Results HTTP-fixture workflows plus lint and both APK
builds. Reproduction:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The debug APK at `app/build/outputs/apk/debug/app-debug.apk` passes signature
verification. Its SHA-256 is
`d7edcdf1432e018b0e450521645f6cbd4262440263a1ba3af7805c2bd73506f4`.

### Review

**Standards:** no documented-standard violations. One optional maintenance
advisory notes that `SavedScreen` now contains Results scroll-retention hooks;
this remains a small additive seam, with no standalone infrastructure refactor.

**Specification:** two concrete findings were corrected: hard-coded chart colors
became unreadable on the night background, and an offline refresh could restore
an older Results scroll position. The retained-context scenario covers the latter;
native observed/no-history profile captures cover the former.

### Native visual evidence

The installed API 26 application passed the complete fixture-backed device
journey in both system themes. Captures include Players, Doubles, the drawer,
Results, and existing pair histories with an observation and without history.
The shared title/action bar, successful-update label, green surfaces, mode
controls, and active drawer destination were compared with the approved
`android-final-ui-prototype.html` reference. Ranking photos/row composition and
Results cards are deliberately left to their own slices. Large-text inspection
also exposed automatic initial search focus; the main container now takes
initial focus so a fresh launch displays the ranking without opening the keyboard.
At 150% text, native captures verify a 320dp-wide portrait layout in both themes
and an 891 × 412 px landscape layout. Drawer controls and the main actions remain
reachable, and content scrolls. Results type filters stack on narrow screens at
large text sizes, avoiding broken words; landscape keeps the horizontal group.

Evidence is retained under the ignored `app/build/reports/issue24/` directory:
`screenshots/`, `layouts/`, the approved browser reference, native run logs,
full-suite XML/HTML, and lint output. Device checks used saved fixtures and normal
production storage only for setup; source-backed behavior was exercised through
the native-UI/local-HTTP integration suite. This was not a new live-site journey
or physical-phone check. API 36 coverage is host integration, not a second
installed emulator.

The software emulator required a task-owned ramdisk disabling forced data
encryption and legacy System UI/ANR startup workarounds. These affected only the
disposable emulator; application TLS/network policy is unchanged. Temporary
emulator data and task tools were removed after verification.

## Embedded doubles history — issue #19

Implemented on `issue-19-doubles-history-chart`, based on `5977ed3`.
The profile now embeds the approved single-chart layout with Azerbaijani
Xal / Mövqe radio controls. It projects observations for the stable pair ID
from the existing saved ranking snapshot, also used for current standing.
The chart orders actual timestamps, spaces points by elapsed time, and puts
rank #1 at the top. Full date/time labels include milliseconds to distinguish
same-day refreshes. A single point is displayed immediately with a future-refresh
note; pre-observation profiles show an honest empty state. Switching modes
performs no HTTP requests or writes, and the selected mode survives screen
refreshes and Android instance-state restoration.

The full application integration suite passed **76 cases**, zero failures,
errors, or skips, together with Android lint and both APK builds:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

`DoublesChartTest` adds two distinct product UI workflows through actual local
HTTP responses, production parsing, controlled time, and durable storage.
The first verifies a dated single observation in both modes, no navigation or
HTTP requests when switching, and no added observations after medal/match
refresh. The second accumulates three observations before either partnership
is opened, including two same-day refreshes and a two-day gap; it checks both
measures for pairs sharing a player, current-standing consistency, preservation
after a failed ranking refresh, and unchanged history/update time after a
year-old offline restart. The existing legacy profile/pagination workflow now
also asserts no chart or invented history for old saved metadata. Existing
movement, freshness, failure, navigation, singles-chart, and match workflows
remain intact. Assertions use product UI/accessibility output and external
HTTP requests, never private storage as evidence.

The first new chart test failed against the previous profile without a chart,
then passed after implementation. Independent Standards and Spec reviews found
no hard violations or specification defects. The Standards review's small
coordinate-mapping duplication advisory was resolved and re-reviewed.
After that refactor, both chart journeys, lint, and both APK builds passed
again. The final debug APK's signature verifies; its SHA-256 is
`1b922f31836657f71616f556c79590c39bf5b11540cb5d9e1d26c64819f4d997`.

The final APK was installed on an Android 8.0 / API 26 x86_64 software emulator
at 412 × 891 px. Controlled saved observations were prepared through production
models for setup, with the app offline. Native screenshots and accessibility
trees establish a visible single dated point in both modes; readable Xal /
Mövqe controls; three chronologically positioned observations with unequal
elapsed-time gaps; and #1 above #3 above #9 in rank mode. Medals, observation
labels, history notes, and the recent-match button remain readable in the
scrolling profile without overlap. Evidence is in `first-point-points.png`,
`first-point-rank.png`, `history-points.png`, and `history-rank.png` under the
ignored `app/build/reports/issue19/` directory, alongside native UI trees,
verification logs, and full-suite XML/HTML.

Initial emulator attempts encountered legacy-image forced-encryption and
System UI startup failures. A temporary ramdisk disabling forced data encryption
and System UI startup workarounds allowed the visual checks; application code,
manifest, and TLS policy were unchanged for device setup. This was a fixture
visual check, not a new live-site journey or physical-phone check. Temporary
emulator data and setup tools were removed after verification.

## Doubles ranking movement — issue #18

Implemented on `issue-18-doubles-ranking-movement`, based on `afb3890`.
Each complete successful doubles refresh saves its timestamp, every pair's
identity/points/published position, retained observations, and latest movement
in the existing atomic ranking snapshot. The serialized model remains compatible
with old saved rankings; no observations are backfilled. Comparisons use only
the immediately preceding recorded ranking, including distinct tied positions.

Full verification passed **74 cases**, zero failures/errors/skips, plus Android
lint, debug APK assembly, and connected-test APK assembly:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

`DoublesMovementTest` adds three native UI integration workflows: the combined
gain/loss/search/recreation/offline/disappearance/reappearance/unchanged-refresh
lifecycle; read-only persistence failure followed by offline reopening and a
successful retry against the preserved baseline; and pre-observation snapshot
compatibility followed by two real observations. The existing parameterized
`DoublesFailureTest` now retains nonzero movement across five HTTP/parsing/
validation failures, offline reopening, and recovery. The interrupted-name
workflow verifies that partial identity resolution cannot create a comparison
or advance a saved baseline. Assertions inspect visible rows, update labels,
accessibility descriptions, and rendered text styling. Stored history is the
source for the dependent chart ticket; this slice adds no chart UI.

`DoublesRefreshRecreationTest` adds fresh/stale parameterized cases that recreate
Rankings while an HTTP response is delayed. Both require the same single source
request, preserved movement, and the correct next comparison. A shared in-flight
refresh survives recreation and releases its completed result, so later launches
read durable storage. Snapshot reads and writes are serialized across store
instances: API 26 `AtomicFile.openRead()` can otherwise restore a backup while
another instance is writing. Network work stays outside this storage lock.

Separate standards and specification reviews found a font-scaling issue and
the in-flight recreation defect; both were fixed and re-reviewed with no
remaining findings. Movement now uses relative text sizing to follow Android's
font-size setting.

The debug APK was installed on an Android 8.0 / API 26 x86_64 software emulator
at a 412 × 891 px phone size. Controlled saved observations prepared through
the production models showed a small green ↑3 for #12 → #9, a small red ↓2 for
#9 → #11, and no indicator on the unchanged row. Both arrows and counts were
visible and distinct from the surrounding text; direction did not rely on
color. This was an offline fixture visual check, not a new live-site journey.
An additional check at font scale 1.5 showed the arrow/count enlarging with the
row. The legacy software emulator needed System UI crash/ANR dialogs dismissed;
System UI was disabled in this disposable emulator to finish the enlarged-font
check. App code and TLS policy were not changed for device setup.
Verification logs, full-suite XML, and both font-size screenshots are retained
under the ignored `app/build/reports/issue18/` directory. Temporary emulator
data and setup tools were removed after verification.

## Doubles profiles — issue #17

Implemented on `feature/17-doubles-profile`, based on `48c903a`. The existing
pair-results destination now shows both player links, published pair points and
position from the latest saved doubles ranking, separate partnership medal
counts, and recent tournament matches and duels. Ranking values show their own
successful update time. Profile refresh retrieves mutable medal metadata despite
cached identity, validates pair membership and the results identity, and saves
medals with results atomically. Missing counts differ from zero; refresh failure
preserves the saved profile. Existing snapshot identities and serialization
versions are retained. The dependent history chart is outside this ticket.

The full application integration suite passed **69 cases**, with zero failures,
errors, or skips. This includes three new profile workflows, three pair-profile
freshness boundaries, and five pair-metadata failure scenarios added to the
existing parameterized suites. Tests use native Android UI, the local HTTP
fixture server, production parsing, a controlled clock, and durable private-file
storage. Coverage includes cached-identity medal updates, absent/null/zero
counts, published standings at tied points, changed saved rankings, rejected
metadata for another partnership, offline restart, and actual pre-feature Java
snapshots. Existing match/player navigation, paging, deduplication, and ranking
search/scroll workflows remain in use, with added medal-preservation assertions.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Lint completed with zero errors and the existing 16 warnings. Both APKs assembled;
the debug app APK signature verifies. It remains at
`app/build/outputs/apk/debug/app-debug.apk`, version 0.3.0 / code 3.
SHA-256: `5eccb64f5749e166285700c8ce66422a27d780388a217125150c22faa0fa9345`.
Logs and full-suite XML are retained under ignored
`app/build/reports/doubles-profile-17/`.

Separate Standards and Spec reviews found no violations or specification gaps.
The optional duplicate-fixture advisory was resolved by sharing `pair-info.json`;
all eight affected profile freshness/failure cases passed again afterward. The
Standards follow-up confirmed no remaining advisories.

The adapted opt-in `LiveDoublesTest` compares a real pair profile's names,
points, and position with its selected ranking row and checks medal counts and
results without fixed live values. The Android test APK compiled successfully;
no connected device was available, so that journey and physical layout checks
were not run. A read-only live metadata request confirmed the current medal
field names; it does not replace the installed-app journey.

## Amateur-only singles ranking — issue #14

The confirmed change in [issue #14](https://github.com/eshref/TableTennisAz/issues/14)
was implemented on `feat/14-amateur-rankings` on 2026-11-16. Singles rankings use
the website's Amateur list, recalculate competition ranks, and keep search ranks.
Professional profiles, results, and doubles participation remain accessible.
Old unlabelled mixed rankings are ignored until an amateur download succeeds;
new amateur snapshots remain available offline.

The full application integration suite passed **58 cases**, with zero failures,
errors, or skips. Android lint completed with zero errors and 16 warnings; the
debug APK and Android test APK assembled successfully:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The amateur ranking/search/offline workflow passed on API 26 and API 36. The new
`RankingsMigrationTest` covers old mixed data while offline, a failed initial
download, successful amateur replacement, and offline relaunch. Existing profile,
results, and doubles workflows retain professional-profile points and pairs.
The ranking and migration regressions failed against their previous behavior
before their respective implementation changes, then passed. Separate Standards
and Spec reviews found no issues.

The debug APK remains version 0.3.0, version code 3, at
`app/build/outputs/apk/debug/app-debug.apk`; its APK v2 signature verifies.
SHA-256: `59a5b7c149da98f49169cb6463aad3234fec1e8293fe362232dd4d7f154c4e86`.
Verification logs and full-suite XML are retained under the ignored directory
`app/build/reports/amateur-ranking-14-1patA3/`.

No device or live-site tests were run for this change. The opt-in
`LiveRankingsTest` now describes and exercises the amateur ranking.

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
