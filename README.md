# TableTennisAz Android

Native Azerbaijani rankings for Android 8.0 (API 26) and newer. This first slice
implements [issue #1](https://github.com/eshref/TableTennisAz/issues/1).

The app retrieves `/players?level=amateur` and `/players?level=pro` directly from
https://tabletennis.az, merges source player identities and sorts their numeric
points descending. Equal points share competition ranks (1, 2, 2, 4). These are
app-defined combined positions, distinct from the website's category positions.
Search uses Azerbaijani case rules and preserves the combined positions.

A complete successful retrieval is saved atomically in private app storage with
its update time. Failed or malformed category responses cannot replace that
snapshot. Saved rankings survive restart and are never removed because of age.
This slice refreshes on opening and offers a manual refresh; the coordinated
15-minute policy is scheduled in issue #8. Profiles and results are later slices.

## Build and install

Use JDK 21, Android SDK Platform 36 and Build Tools 36.0.0. Set `ANDROID_HOME` to
your SDK directory (or set `sdk.dir` in an untracked `local.properties`). The
Gradle wrapper is pinned and verifies the distribution checksum.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n az.tabletennis/.RankingsActivity
```

The debug APK is for device testing; store publication and production signing
are outside this slice. Release builds require an owner-controlled signing key.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Application integration tests drive native Android views under Robolectric on
API 26 and API 36. A real HTTP fixture server supplies representative website HTML;
OkHttp, jsoup and private-file persistence are production code. Assertions inspect
rendered labels and external HTTP requests, never storage internals. The offline
workflow destroys and recreates the Activity and its repository after shutting
down the HTTP server. This is host-side Android simulation; it does not replace
APK installation and launch on an emulator/device.

The opt-in connected live-site check uses the installed Android app's production
HTTPS client and parser for both categories, without fixed player counts/scores:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=az.tabletennis.LiveRankingsTest
```

It needs website connectivity. It is kept separate from deterministic tests so
upstream downtime does not make the fixture suite flaky. See
[verification notes](docs/android-verification.md) for the checks performed in
this implementation environment.
