# Native acceptance and debug delivery for issue #32

This change aligns the installed Android views with the [approved final prototype](../../prototypes/android-final-ui-prototype.html).
Both histories show selected observations and flat observation rows with separate,
wrapping date and value columns. The chart plot and endpoint labels share the
chart area. Results groups consecutive cards on the same calendar day and wraps
long event names in the selector and its menu. The display retains source date
labels, values, timestamps, ordering, pagination and persistence.

Observation rows retain one divider, native touch feedback, selection semantics,
a complete accessible date/value label and a 48dp minimum target. The live
Results journey selects the first actionable result below the date heading.
[Issue #32](https://github.com/eshref/TableTennisAz/issues/32)

## Verification

| Check | Result |
| --- | --- |
| Complete host integration suite | [176 tests in 42 suites](host-results.json). Zero failures, errors or skips. |
| Focused host recheck after review | 17 Results workflow and layout cases passed, including selected and dropdown labels at 150% text and two distinct same-date cards under one heading. |
| Android lint | 0 errors, 26 warnings. |
| Debug and instrumentation APKs | Both assembled successfully from the corrected source. |
| Final phone-review APK on Android 16 / API 36 | The [final fixture run](final-fixture-result.properties) timed out in preflight before installing the APK. The four-configuration matrix remains unverified for this build. |
| Flat-row build before the date grouping fix on Android 16 / API 36 | Two installed preflight cases passed in the [diagnostic run](flat-diagnostic/result.properties). The subsequent [light journey](flat-diagnostic/tests-light.txt) ended in a Rankings focus ANR. The retained [singles-history PNG](flat-diagnostic/light-singles-history.png) shows the flat rows. |
| Earlier installed matrix | Four configurations, 40 PNGs, using the rounded-row APK. The [result](before-flat-matrix.properties) also records its host phase, which compiled the flat-row source. |
| Installed live-source journeys | Both live attempts passed installed preflight. The [first](live-phone-service-failure.properties) stopped at a missing phone service. The [Wi-Fi retry](live-wifi-result.properties) reached the first instrumentation command, where Android failed to attach the app process. Zero live cases ran. |

The layout tests cover selected values and observation rows, date-section order
and deduplication, and long event names at 150% text. Before the fixes, the
assertions found stacked selected values, absent date headings and 27 ellipsized
characters in the narrow selected event label. A follow-up regression found
duplicate headings for two spellings of the same source date, then passed once
sorting and grouping shared the calendar-day parser. The host suites cover native
UI bound to real HTTP fixture requests, production parsing and durable saved data.
They run under Robolectric. Relevant retained guarantees include:

| Behavior | Product checks |
| --- | --- |
| Loading, immediate saved content, delayed responses and the refresh boundary | `RankingsFreshnessTest`, `ScreenFreshnessTest`, `DoublesFreshnessTest` |
| First-load failure, retry, successful update and failed saved-data refresh | `RankingsFailureTest`, `SavedScreenFailureTest`, `DoublesFailureTest` |
| Long offline reuse, restart and source identities | `RankingsWorkflowTest`, `ProfileWorkflowTest`, `DoublesWorkflowTest`, `MatchJourneyTest` |
| Independent photo fallback, failed replacements and unchanged data time | `PlayerPhotoFailuresTest`, `DoublesPhotoFailuresTest`, `ProfilePhotosTest` |
| Empty selections and filter recovery | `ResultsFilterTest`, `ResultsDeviceTest`, `MainNavigationTest` |
| Atomic pagination, refresh coverage and selection isolation | `BatchedResultsTest`, `PlayerHistoryPagingTest`, `DoublesHistoryRefreshTest`, `ResultsFilterTest` |
| Source history order, yearless dates and device-local doubles observations | `RatingHistoryTest`, `DoublesChartTest` |
| Long names, separate scores, accessible observations and reachable controls | Screen-specific `*LayoutTest` classes |

Ranking layout tests retain API 26 coverage. Profile, doubles profile, Results,
match and singles-history layouts, and the navigation workflow cover APIs 26
and 36.

## Installed journeys

```sh
scripts/android-verify --tests az.tabletennis.NativeAcceptanceDeviceTest
scripts/android-verify --live
```

The runner uses a 60-minute installed-preflight budget, then a separate
45-minute full-phase budget. Fixture runs exercise the matrix, shut down the
owned emulator, then run host checks in the same full-phase budget.

| Configuration | Dimensions at 160 dpi | Text |
| --- | --- | --- |
| Light | 412 × 891 | 100% |
| Dark after offline process restart | 412 × 891 | 100% |
| Narrow light after offline process restart | 320 × 568 | 150% |
| Landscape dark after offline process restart | 891 × 320 | 150% |

The combined journey visits Players, a player profile and separate singles
history, returns to the searched ranking, switches to Doubles, follows a pair's
inline ranking history to a partner profile, and returns to the same search.
It opens Results from the drawer, follows a match and participant, returns through
Results, selects a long event with empty results, clears it and returns to both
retained ranking searches after recreation. Only the first configuration
prepares the saved fixtures. The app renders cached portraits before comparison
captures. Fixture portraits and reference data are packaged in the test APK.

`--live` runs the existing `LiveRankingsTest`, `LiveExperienceTest` and
`LiveDoublesTest` in sequence after installed preflight. Connectivity and all
three classes share one 45-minute deadline. The first failed journey ends the
run. The runner uses Wi-Fi and checks for an active Android default network.
The first attempt had a guest phone-process startup ANR and the network setup
command returned `Can't find service: phone`. The next attempt recovered
connectivity through the emulator's `AndroidWifi` access point. It reported an
active default network after an explicit guest Wi-Fi join. The runner now
requests that join once when there is no default network. Android then returned
`failed to attach` at the first live instrumentation command. The website returned
HTTP 200 to a host HTTPS request. [Initial setup error](live-phone-service.txt) · [Instrumentation outcome](live-wifi-rankings.txt)

## Visual comparison

The approved reference is pinned to `809029976d182934bdc4ba2a9dcfd60acb8adf40`.
The [ready](reference/) and [offline](reference-offline/) browser references use
its sample identities and public portraits at 412 CSS pixels, density 1 and
100% text. Chromium captured the 891px-high preview as a 892px-high PNG. The
native captures are 891 pixels high. The [initial native
captures](initial-light/) and [rounded chart rows](before-flat/) document visual
findings from earlier APKs. The [side-by-side comparison](comparison.html)
contains all seven views in light and dark from the earlier rounded-row APK.
Each browser reference uses the ready or offline state corresponding to the
native capture. The [narrow](before-flat/narrow-large/) and
[landscape](before-flat/landscape-large/) sets use 150% text.

Native font metrics and device system bars differ from the browser preview.
Source dates and complete doubles observation times retain their original
precision. Scrolled native captures show the visible part of each screen. The
installed journey checks reachability of controls beyond those viewports. The
native rankings use compact title chrome and native search hints. Their list
context scrolls with the ranking. The paired Players captures were scrolled to
the first row. The offline notice and explanatory text sit above that viewport.
Native Results puts the text notice below its filters. The prototype uses a
tinted notice above the controls and adds ranking subtitles, a search icon and
a rank column heading. The earlier Results fixture has two sample cards. The
current fixture also has a same-day duel and a league card.

## Review

The Standards review found a runner timeout-status race and gaps in the
assertions for same-date Results cards and long dropdown labels. The watchdog
and successful completion now share a lock. Both Results test classes passed
after strengthening the assertions. Cached-portrait readiness waits for bitmap
rendering. The comparison shows the actual portraits, and existing photo tests
check image identity and fallback behavior.

The Spec review found the same-day heading defect and the remaining differences
in ranking chrome and offline notice styling. The date grouping fix passed the
HTTP workflow and read-only recheck. The final APK's installed matrix and motion
at normal system settings remain open. The verification table records the live
attempts on the previous APK.

## Accessibility

Contrast ratios below use the shared sRGB palette.

| Foreground / background | Light | Dark |
| --- | ---: | ---: |
| Body / page | 13.13:1 | 15.01:1 |
| Secondary / page | 5.45:1 | 9.82:1 |
| Primary links / surface | 6.52:1 | 9.03:1 |
| Selected segment / primary | 6.52:1 | 7.91:1 |
| Selected observation / page | 6.14:1 | 10.40:1 |
| Upward movement / page | 5.56:1 | 10.40:1 |
| Downward movement / page | 6.15:1 | 11.23:1 |

Android heading semantics apply from API 28. API 26 retains named text and
controls. Ranking rows announce full names, positions, points and directional
movement. Charts expose source dates and values as selectable observations.
Decorative portraits are excluded from accessibility navigation. System night
mode controls the appearance. The runner disables animation scales, leaving
motion at normal system settings unverified. Hands-on TalkBack is unverified.

## Earlier attempts

The [first preflight](interrupted-preflight.properties) was interrupted during
boot. The [initial matrix](initial-matrix.properties) passed preflight and 168
host tests, then exceeded the existing installed case limit after capturing the
seven light entry screens. The shortened combined journey retains cross-screen
context. Existing tests cover the detailed cases removed from this journey. That
first run rebuilt and re-signed the instrumentation APK before installation to
include review corrections. The runner now sets the software guest's verified
long-press threshold to 10 seconds because a slow Espresso tap held for 2.580
seconds and activated a tooltip.

Two runs passed preflight and failed host checks. An assertion mistook a valid
historic `800 xal` row for current standing before Robolectric extraction filled
the root disk. The current-standing check now reads the correct UI fields and the
build uses owned filesystem space for temporary data. Another API 36 navigation
case failed while doubles identities were loading with both Robolectric and the
emulator running on the 8 GiB host. The suite passed alone. The runner now stops
the guest before host checks. [Disk failure](host-disk-failure.properties) · [Timing failure](host-timing-failure.properties)

A [build-only attempt](interrupted-build.properties) stopped before guest launch
to include a watchdog and cleanup correction. Cleanup has its own recorded time.
The [first corrected preflight](timer-preflight-failure.properties) timed out
during the doubles case after the guest marked TSC unstable and switched to HPET.
Simple ADB reads then timed out. A single-vCPU trial also had slow ADB responses
without that clock switch and stopped before installed tests. Fresh retries use
the original two-vCPU configuration.

A later corrected run passed preflight and captured Players, Player profile and
Singles history before a Rankings focus loss timeout. Logcat collection timed
out, and cleanup removed the guest before a thread trace was captured. The cause
is undetermined. The next fresh attempt reached the framework and timed out
setting the animation scales before APK installation. Its clock switched from
TSC to HPET. The guest listed an ANR file, and the trace transfer timed out.
The runner now requests the initial thread stack excerpt first, saves available
partial screenshots, and gives the software guest more time for the settings
operation within the same preflight budget.
[Corrected diagnostic result](flat-diagnostic/result.properties)
· [Settings timeout result](preflight-settings-failure.properties)

The final fixture run reached its initial 30-minute ADB timeout. Its one
automatic recovery verified the device runtime settings. Android services then
failed to become ready within the boot wait and the runner stopped before APK
installation. The guest had marked TSC unstable and switched to HPET during
startup. The cause of the earlier Rankings focus ANR remains undetermined.
[Final fixture result](final-fixture-result.properties)

## Phone-review artifact

The local phone-review APK is
`.android-verification/delivery/issue32-jkJNk9/TableTennisAz-issue32-debug.apk`.
It is version 0.3.0, code 3, with minimum API 26 and target API 36. Signature
verification passed. APK SHA-256
`b806d59df6c3d7b6ac0f21f3e45740218ce695ef9e16a33ba553178f6478a146`.
Signing certificate SHA-256
`90cd9cbbb7d186f2ac23be8ea3da981c12559ab778ea9ff40f7ac8b41483e526`.
The phone APK has the same 27 non-signature ZIP entries as the final managed
fixture build and uses the existing workspace debug key. Updating a compatible
installation signed by that key can preserve saved data. The previous APK and
its signing record are retained under `before-date-grouping/` in the delivery
directory. Physical-phone installation and signing compatibility, installed
API 26, and TalkBack remain unverified for this delivery.
[APK payload comparison](apk-payload-comparison.json)
· [Current installed verification](../../agents/android-verification.md)
