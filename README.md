# TableTennisAz Android

Native Azerbaijani rankings, player profiles, rating history, and match results
for Android 8.0 (API 26) and newer. The app opens on combined individual-player
rankings. This implements the first-delivery scope in issues #1–#9.

Rankings combine the amateur and professional website lists by source identity,
ordered by points descending. Equal points share competition ranks (1, 2, 2, 4).
These app-defined positions differ from the website's category-specific ranks.
Search uses Azerbaijani case rules and preserves a matching player's rank.

Select a player to see current points and singles/doubles match history, with
Load more for older source pages. Current
points come from the source rankings: the profile's `Reytinq` ratio is not a
points total. Open Xal tarixçəsi for the player's points chart and accessible
observation labels. Supplied dates and source order are retained; missing years
are never invented. Historical points are shown to two decimal places to remove
observed binary floating-point noise. A missing series has a no-history state.

Individual games nested inside team fixtures are supported. For player history,
matching names only select candidates; match-detail participant IDs verify that
the result belongs to the selected player. Verified details are saved as well.

Match details show source scores, dates, and times when available. Every singles
or doubles participant links to their own profile; Android Back returns through
the match and originating history/feed.

## Results coverage

Results combines event matches and duels, with All results / Tournament matches /
Duels selections and an event chooser. Cup and league events are both included.
An event can be selected even if it was absent from the initial general batch.

A new general batch retrieves one duel page and at most two event-result pages.
The event chooser is retrieved separately. Event continuations alternate with
previously unseen events; selecting one event loads one of its pages per batch.
Load more advances the saved source cursors. Repeated match identities are
merged, retaining event and duel membership. Loaded matches are sorted by their
supplied dates, without inventing within-day times or treating IDs as dates.

The event chooser has no verified global date order. The visible feed is partial
and may omit recent matches elsewhere on the website. A failed batch does not
save partial results or advance its cursors. Each filter/event selection has its
own durable snapshot; an offline selection never visited before is unavailable,
rather than a verified empty result.

## Freshness and offline use

Saved data is displayed immediately when a screen opens. Online opening fetches
missing data, or refreshes data **more than 15 minutes old**. Exactly 15 minutes
is still fresh. Manual refresh works at any age when online. There is no periodic
background download schedule.

A successful refresh saves complete parsed data and its update time atomically.
Errors and unusable changed markup preserve the saved data and time. A first
visit without usable data shows an unavailable state. Age never deletes saved
rankings, profiles, charts, match details, or results; clearing app storage or
uninstalling removes them.

Profile/history and Results refresh revisit the source pages already downloaded
for that player or selection,
retaining its loading progress. Load more adds a bounded batch and retains the
last full refresh time so older rows do not appear newly refreshed. Returning to
a stale selection refreshes its loaded coverage, rather than scanning the archive.

## Build and install

Use JDK 21, Android SDK Platform 36 and Build Tools 36.0.0. Set `ANDROID_HOME` to
your SDK directory (or set `sdk.dir` in an untracked `local.properties`). The
Gradle wrapper is pinned and verifies the distribution checksum.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n az.tabletennis/.RankingsActivity
```

The first complete test APK is version **0.2.0**, version code **2**, at
`app/build/outputs/apk/debug/app-debug.apk`. Transfer it to your phone, open it,
and allow installation from the browser/file manager if Android asks. Existing
saved data can be retained with an update signed by the same debug key. If an
older installation uses a different signing key, uninstalling it removes its
saved data before installing this APK.

Try searching a player, opening their profile and chart, then following a match
and its participants. In Results, try the three result types, choose an event,
and use Load more. After loading these screens, disconnect and restart the app
to check previously visited screens and their update times.

This debug APK is for testing. Google Play publication, production signing, pair
rankings, reservations, and other deferred features are outside this delivery.
The app fetches public website HTML/JSON directly. Website markup changes may
require an updated APK; feed coverage is partial as described above.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Application integration tests drive native Android views under Robolectric with
a real local HTTP fixture server, production parsing, and private-file storage.
Assertions inspect UI outputs and external HTTP requests. Coverage includes
identity/navigation, source dates and chart labels, mixed-source paging,
selection isolation, delayed responses, freshness boundaries, and offline reuse.
The rankings workflow runs on API 26 and API 36; the additional workflows run on
API 26. Host-side simulation does not replace APK installation on a device.

The opt-in installed-app journey exercises production HTTPS requests to both
rankings categories, a profile/chart, mixed Results, a match, and a participant's
profile. It asserts visible data without fixed live names, counts, or scores:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=az.tabletennis.LiveExperienceTest
```

This needs a connected emulator/device and website connectivity. The GitHub
workflow keeps it opt-in using the `live_site` input. Automatic CI runs the
fixture suite, lint and APK build, and publishes the APK and reports.
See [verification notes](docs/android-verification.md) for actual checks and
compatibility limits.
