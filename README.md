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

Doubles Comfortable rows group the two full partner names and pair points beside
one split circular avatar, with the published position in a separate badge.
Each half uses that partner's public portrait or its own neutral silhouette.
Both halves reuse the singles portrait cache and remain usable offline. Older
saved identities discover optional photo metadata separately, so usable rankings,
search, and navigation do not wait for photos. Movement retains its accessible
direction and number of places.

Doubles profiles reuse the split portrait and link each full partner name to the
correct individual profile. A grouped summary separates current pair points and
published position from the ranking update time. Medal tiles show the pair's own
counts; an em dash means unknown, while zero remains a known count. The inline
Xal/Mövqe chart retains actual timestamp spacing and exposes dated, selectable
observations, with dates and values in separate wrapping columns. Its mode
survives refresh and Activity recreation. Pair matches
use the shared Results cards, including saved duels and paginated older results.

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

Select a player to see their portrait or local silhouette, current points, and
known amateur position. Professional-only and unlisted profiles have no amateur
position; unavailable points are explicit. Profiles reuse the Results match
cards for singles/doubles history, with Load more for older source pages. Current
points come from the source rankings: the profile's `Reytinq` ratio is not a
points total. Open Xal tarixçəsi for the player's points chart and accessible
observation labels. Supplied dates and source order are retained; missing years
are never invented. Historical points are shown to two decimal places to remove
observed binary floating-point noise. A missing series has a no-history state. The dedicated screen uses compact
Back/Refresh controls, a labeled chart, and individually accessible observation
rows with separate date and points columns. Select a row to highlight its source
point and inspect its date and value;
this makes no request and keeps the successful-update time. Selection and the
reading position survive recreation. A single observation is explicitly
identified without claiming a change.

Optional portraits come from the existing profile or ranking responses and use
the shared player cache. Photos load independently of profile data and remain
usable offline. Older saved profiles without photo or position metadata remain
readable. Back and recreation retain the profile's reading position, including
when a stale profile automatically refreshes its already loaded history pages.

Individual games nested inside team fixtures are supported. For player history,
matching names only select candidates; match-detail participant IDs verify that
the result belongs to the selected player. Verified details are saved as well.

Match details group each side's participants beside its recorded score, with
an explicit Qalib label for the higher-scoring side. Source event, date, and
available time stay separate from the scoreboard; missing times are omitted.
Every singles or doubles participant links to their own profile, with a shared
cached portrait or local silhouette. Optional images load independently, and
ambiguous unlinked doubles photos are not assigned by their display order.
Android Back returns through the match and originating history/feed, retaining
the reading position. Older saved match details remain usable offline.

## Results coverage

Results combines event matches and duels, with All results / Tournament matches /
Duels selections and an event chooser. Cup and league events are both included.
An event can be selected even if it was absent from the initial general batch.

Date headings group consecutive cards on the same calendar day.
Match cards separate each side's full names and final score from the source date,
match type, and event. Doubles names wrap, and the higher-scoring side's names
receive bold emphasis. Tap a card to open the existing match detail; Back retains
the Results selection and browsing context. The type chips and event chooser
remain reachable while scrolling on small screens. A verified empty filtered
selection offers **Filtrləri təmizlə** to return to the general feed.

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

Gradle reads the public version (`versionName`) and Android version code
(`versionCode`) from the repository-root [version.properties](version.properties).

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Transfer it to
your phone, open it, and allow installation from the browser/file manager if
Android asks. An update signed by the same debug key can retain saved data. If an
older installation uses a different signing key, uninstalling it removes its
saved data before installing this APK.

The [issue #32 delivery record](docs/verification/issue32/README.md) contains the
local phone-review APK path, native comparison evidence and verification limits.

Try searching a player, opening their profile and chart, then following a match
and its participants. In Results, try the three result types, choose an event,
and use Load more. After loading these screens, disconnect and restart the app
to check previously visited screens and their update times.

This debug APK is for testing. Google Play publication, production signing,
reservations, and other deferred features are outside this delivery.
The app fetches public website HTML/JSON directly. Website markup changes may
require an updated APK; feed coverage is partial as described above.

## Preparing an Android release

Record user-visible changes in the same PR. Describe what users can see or do
differently under **Unreleased** in [CHANGELOG.md](CHANGELOG.md).

1. Choose the public version manually in `MAJOR.MINOR.PATCH` format. While the
   app is at `0.x`, increment `MINOR` and reset `PATCH` to zero for new
   capabilities or breaking changes. Increment `PATCH` for fixes only. Choose
   `1.0.0` deliberately when the app is ready for a stable release.
2. In the release PR, update `versionName` and increase `versionCode` by one in
   `version.properties`. The first official release uses Android version code
   `4`. Increase it by one for each subsequent official release, regardless of
   which part of the public version changes.
3. In the same PR, move the selected Unreleased entries into a heading such as
   `## 0.4.0 - YYYY-MM-DD`, using the chosen version and release date. Keep
   dated releases in descending version order and retain **Unreleased** at the
   top for future changes.

## Verification

For Android UI changes and installed-device verification, use the reproducible
Linux setup and verification command:

```sh
scripts/android-verify
```

It prepares pinned tools, verifies an installed screen before the full device
matrix, and prints the evidence location. See the [agent verification guide](docs/agents/android-verification.md)
for time limits, cleanup, cache locations and selecting other saved-fixture tests.
The existing host-only command remains available:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Application integration tests drive native Android views under Robolectric with
a real local HTTP fixture server, production parsing, and private-file storage.
Assertions inspect UI outputs and external HTTP requests. Coverage includes
identity/navigation, source dates and chart labels, mixed-source paging,
selection isolation, delayed responses, freshness boundaries, and offline reuse.
Host coverage includes API 26 and API 36. The rankings and drawer workflows and
several screen layout suites run on both. The verification report records
separate installed API 36 results.

The opt-in installed journeys exercise production HTTPS requests for the amateur
and doubles rankings, profiles, singles history, Results and match details.
They assert visible data using the current source records.

```sh
scripts/android-verify --live
```

This managed Linux command uses a disposable API 36 emulator and installed
preflight, then runs the existing live rankings, complete experience and doubles
journeys within the documented full-phase budget. It requires website access
from Android. The tests prepare fresh app data. For an already connected
disposable emulator, the existing Gradle route remains available:

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

`az.tabletennis.ProfileDeviceTest` is the separate installed, saved-fixture
profile journey. After installing the app and test APKs, run:

```sh
adb shell am instrument -w -r -e class az.tabletennis.ProfileDeviceTest \
  -e capturePrefix light az.tabletennis.test/androidx.test.runner.AndroidJUnitRunner
```

It follows a searched player through the dedicated chart, a match, a participant
profile, and Android Back, then checks retained search after recreation. Captures
are written to the app's external-files `issue28` directory. Optional arguments
`-e playerName '<long name>'` and `-e fallback true` prepare layout/fallback
scenarios. For names containing spaces, quote the whole remote `am instrument`
command as well as the name so ADB preserves it as one argument.
`-e reuseSaved true` keeps previously prepared files for a separate
process restart with network disabled. Set system appearance, display size, and
font scale externally before each run; these are verification controls.

The saved-fixture doubles profile journey uses the approved reference pair's
portraits, linked partners, inline chart modes, match navigation and Back context:

```sh
scripts/android-verify --tests az.tabletennis.DoublesProfileDeviceTest
```

It captures the profile, both history modes, match cards and long-name fallbacks
in the app's external-files `issue31` directory. The verifier supplies the theme,
viewport, text-scale and offline-restart matrix. Installed readiness must pass
first; see [the issue #31 verification record](docs/verification/issue31/README.md)
for the actual result and remaining device checks.

`az.tabletennis.RatingHistoryDeviceTest` is the installed saved-fixture chart
journey. It opens the selected player's dedicated history, inspects an
observation, and returns with Android Back to the correct profile:

```sh
adb shell am instrument -w -r -e class az.tabletennis.RatingHistoryDeviceTest \
  -e capturePrefix light az.tabletennis.test/androidx.test.runner.AndroidJUnitRunner
```

Native screenshots are saved in the app's external-files `issue29` directory.
Use `-e reuseSaved true` after a separate offline process restart; the optional
`playerName` argument prepares a long-name scenario. Set theme, font scale,
display size and rotation externally, as for `ProfileDeviceTest`.


`az.tabletennis.MatchDeviceTest` checks the installed saved-fixture singles and
doubles scoreboards, every participant link, Android Back, and recreation:

```sh
adb shell am instrument -w -r -e class az.tabletennis.MatchDeviceTest \
  -e capturePrefix light az.tabletennis.test/androidx.test.runner.AndroidJUnitRunner
```

It saves native screenshots under the app's external-files `issue30` directory.
Run in both system appearances, with larger text, narrow display and landscape;
use `-e reuseSaved true` for a separate offline restart. The fixture contains
one cached participant portrait and independent silhouette fallbacks. Production
HTTP, ambiguous image attribution and photo-failure coverage remain in the host
match journeys. See the verification notes for completed checks and limitations.
