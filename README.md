# TableTennisAz Android

Native Azerbaijani rankings, player profiles, rating history, and match results
for Android 8.0 (API 26) and newer. The app opens on amateur individual-player
rankings. This implements the first-delivery scope in issues #1–#9.

Open the menu in the top bar to switch between Rankings and Results. The refresh
icon updates the active selection, and the app follows the phone’s light/dark
appearance. Returning to Rankings preserves each mode’s search and scroll;
Results also retains its selection and browsing position.

Rankings also has an Oyunçular / Qoşalar switch. Doubles ranks partnerships using
the website's published positions and points, so equal points can have different
positions. Rows show both full names; searching either partner keeps source
positions. Selecting a partnership opens its profile with both player links,
current saved published points and position, separate pair gold/silver/bronze
medal counts, and tournament matches and duels. Match details and Load more for
older results remain available. Pair medals come from the partnership metadata,
never the sum of individual players' medals; missing counts differ from zero.

The website supplies pair membership through individual metadata requests. The
first doubles load resolves all full names and shows progress; search becomes
available once the complete ranking is ready. Completed identity lookups are
saved and reused after interruptions and on later visits. Other screens remain
usable during this load. Failed loads preserve the last complete ranking.
Returning to Rankings retains the active selection, search, and scroll position;
a fresh app launch opens Players. Doubles uses the same offline and refresh
policy as the rest of the app. Profile refreshes retrieve mutable medals even
when the pair identity is cached; failures retain saved counts and results.
Points and position use the latest successfully saved doubles ranking, with its
own update time. Bonus and event filtering are outside this doubles version.

Doubles ranking refreshes now save complete dated observations on this device,
including points and published positions for pairs whose profiles have never
been opened. Small green ↑ / red ↓ indicators show places gained/lost since
the immediately previous successful saved refresh, with Azerbaijani accessible
descriptions. Search preserves movement. Unchanged positions, the first
observation, and pairs absent from the previous ranking show no indicator.
Reopening or going offline preserves the latest comparison; failed refreshes
preserve the ranking, observations, movement, and update time together. A later
unchanged refresh clears earlier movement. Older saved rankings remain readable;
observation history starts with new successful refreshes, without backfilling.

Each doubles profile embeds one history chart with Xal / Mövqe controls. It
shows the pair's recorded points or published positions, with #1 at the top.
Dates and times identify each observation, including refreshes on the same day;
horizontal spacing reflects elapsed time. The first observation appears as a
single dated point with a note about future refreshes. Older saved profiles
without observations show an explicit empty state. These device-local charts
remain available offline and after restart; opening profiles, switching modes,
or refreshing medals and matches does not collect ranking history.

Singles rankings use the website's Amateur list, ordered by points descending.
Comfortable rows show a circular player portrait, wrapping full name and points,
with a separate amateur-rank badge. Optional thumbnails come from the ranking
response and load independently of names, search, and navigation. Downloaded
portraits remain available offline; missing or unusable photos use a bundled
person silhouette. Failed replacements retain the player's last usable portrait
without changing the ranking's successful-update time. Older saved amateur
rankings remain readable when photo metadata is absent.
On short screens, the mode and search controls scroll with the ranking.

Players listed in both categories remain eligible; professional-only players
are excluded, with no category toggle. Equal points share competition ranks
(1, 2, 2, 4), calculated among amateurs. Search uses Azerbaijani case rules and
preserves a matching player's amateur rank. Professional profiles, match results,
and doubles participation remain accessible.

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

The amateur-ranking update ignores old mixed singles rankings because they do
not record player categories. An internet connection is needed to download the
first amateur ranking; the app explains this when no usable ranking is available.
Newly downloaded amateur rankings remain available offline.

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

The doubles-ranking test APK is version **0.3.0**, version code **3**, at
`app/build/outputs/apk/debug/app-debug.apk`. Transfer it to your phone, open it,
and allow installation from the browser/file manager if Android asks. Existing
saved data can be retained with an update signed by the same debug key. If an
older installation uses a different signing key, uninstalling it removes its
saved data before installing this APK.

Try searching a player, opening their profile and chart, then following a match
and its participants. In Results, try the three result types, choose an event,
and use Load more. After loading these screens, disconnect and restart the app
to check previously visited screens and their update times.

This debug APK is for testing. Google Play publication, production signing,
reservations, and other deferred features are outside this delivery.
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

The opt-in installed-app journey exercises production HTTPS requests to the
amateur ranking, a profile/chart, mixed Results, a match, and a participant's
profile. It asserts visible data without fixed live names, counts, or scores:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=az.tabletennis.LiveExperienceTest
```

This needs a connected emulator/device and website connectivity. The GitHub
workflow keeps it opt-in using the `live_site` input. Automatic CI runs the
fixture suite, lint and APK build, and publishes the APK and reports.
The separate `az.tabletennis.LiveDoublesTest` connected-test class checks the live
doubles ranking and opens a pair profile, comparing its names, points and
position with the selected row and checking medal metadata and pair results. Its first load can take
several minutes; run it with the same instrumentation argument shown above.
See [verification notes](docs/android-verification.md) for actual checks and
compatibility limits.
