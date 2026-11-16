# Rankings slice verification

Implementation of [issue #1](https://github.com/eshref/TableTennisAz/issues/1)
was explicitly requested on 2026-11-15. Later first-delivery tickets remain outside
this change. Build environment: Linux, JDK 21, Gradle 9.1.0, AGP 9.0.1,
Android SDK 36 / Build Tools 36.0.0. Minimum runtime: API 26.

## Deterministic application integration scenarios

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

## APK and live source verification

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

## Reproduction

See the root README for build, fixture-test and connected-test commands. CI runs
the deterministic checks and publishes a debug APK artifact. A manually selected
workflow input runs the live check separately because website availability is
outside the app's control.
