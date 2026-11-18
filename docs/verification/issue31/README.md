# Doubles profile — issue #31

Implemented on `issue-31-doubles-profile`, starting at
`78fe089308f525e8a835df924dd595749cacf786` for
[issue #31](https://github.com/eshref/TableTennisAz/issues/31).

The profile uses the shared split circular portrait, individually linked full
partner names, a grouped standing summary, partnership medal tiles, segmented
inline history and the shared Results match cards. Unknown counts appear as an
em dash with an explicit accessible description; zero remains a known count.
Standing and ranking update time still come from the latest saved doubles
ranking. Medal/match refreshes retain their separate successful-update time.

Chart endpoints and selectable observation rows show the actual timestamps,
including milliseconds. The existing elapsed-time spacing, better-ranks-higher
projection and device-local coverage explanations remain. Inspection and mode
changes only update the inline presentation; mode survives refresh/recreation.
Shared cards recognize the source's `Duello` event label in legacy profile data.

## Host checks

Focused native-UI/real-HTTP workflows passed for independent photo/fallback
combinations and offline profile reuse; partnership standings and medals;
legacy identities/results; pagination, participant/match links and Back; atomic
multi-page failures; separate partnership histories; observation inspection;
and mode retention without collecting observations on refresh/recreation.

Eight new layout cases passed at API 26 and 36 under Robolectric native graphics:
light/dark 320×568 and 891×320 landscape, all at 150% text. They check both full
linked names, separate standing columns, unknown versus zero medal values,
complete timestamp/observation text and reachable actions. These are host checks,
not installed-device evidence. The debug and instrumentation APKs compile,
including the new `DoublesProfileDeviceTest` and adapted `LiveDoublesTest`.

The final complete run passed **168 tests in 42 suites**, with zero failures,
errors or skips. Lint passed with **zero errors and 26 warnings**. Both the debug
APK and instrumentation APK assembled successfully. The command was:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=<managed-sdk> \
  ./gradlew --no-daemon --project-cache-dir <session>/gradle-cache \
  -I <session>/build-paths.gradle \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The Gradle init script placed every project's build output under the isolated
session directory `/c/src/.codex-issue31-cDa4Q3/build`. That temporary directory
was removed after retaining the APKs, JUnit XML/HTML, lint reports and execution
logs in `.android-verification/evidence/issue31-host-kStWmC/`.
The [machine-readable results](host-results.json) include final APK checksums.

Standards review: no documented-standard violations or actionable code smells.
Spec review: no confirmed implementation defects or scope creep. The review's
pending full-suite/lint gap is closed by the final run; installed visual
acceptance is established by the scoped follow-up below.

## Installed Android follow-up

Two fresh managed API 36 software runs failed readiness. The initial run retained
its [result](preflight.properties) and [instrumentation output](preflight-tests.txt):
screenshot-directory creation failed, then instrumentation timed out. Cleanup
completed. The authorized repeat at
`.android-verification/evidence/run-20261117T184105Z-SC9byF/` failed with unavailable
UiAutomation and screenshot-directory errors. These managed results remain failed.

The user authorized further diagnosis. Restarting that retained, initialized
emulator allowed the **clean, unmodified smoke journey to pass both cases in
63.512 seconds**. Its screenshots establish actual 412×891 captures, window focus
and the expected saved scores (3–2 and 1–3). Temporary in-app diagnostics confirmed
mounted external app storage and connected UiAutomation. A shell `run-as` storage
probe had different namespace/SELinux access and did not establish an app storage
failure. No permanent infrastructure fix or fresh cold-start success is claimed.

The first profile attempt captured a closed drawer and then failed Back. A second
attempt explicitly checked drawer visibility and failed before Back. Temporary
touch logging showed that Espresso's hamburger tap lasted **2.580 seconds**,
without invoking its click listener. The same installed app opened the drawer and
closed it with Android Back through manual system input. For the follow-up matrix,
the disposable emulator's long-press threshold was raised to 10 seconds; production
behavior, Espresso tap/Back actions and committed verification caps are unchanged.
All temporary logging was removed and the clean APKs reinstalled before that run.

The pair fixture uses public portraits, names, points, published position and
medals from the [approved prototype](../../prototypes/android-final-ui-prototype.html).
The fixture uses test data for matches and observations.

The drawer follow-up corrects the differences visible in the supplied phone photo:
rounded trailing corners, 85% width capped at 310dp, green branding, navigation
icons, a pale selected row, transparent unselected row and bottom footer. The
[actual native drawer capture](drawer-light.png) comes from the API 36 emulator.
It is not a capture from the user's physical phone.

The final host repeat passed **168 tests in 42 suites**, zero failures/errors/skips;
lint reported **zero errors and 26 warnings**; both APK builds passed. Evidence and
the update-compatible phone APK are retained under
`.android-verification/evidence/issue31-followup-e2XAzI/`. Its phone APK and the
emulator APK have identical ZIP contents apart from signing metadata, and the phone
APK retains the original shared debug signing key.

## Final initialized-device result

After the clean preflight, all four complete profile journeys passed within the
documented 45-minute matrix budget. Each configuration ran one combined product
scenario, including drawer Back, both partner links, match Back, mode/observation
inspection, recreation, ranking search restoration and long-name fallbacks.

| Configuration | Dimensions | Text | Saved-data reuse |
| --- | --- | --- | --- |
| Light | 412×891 | 100% | Newly prepared fixture |
| Dark/offline | 412×891 | 100% | No default network; process restarted |
| Narrow/light | 320×568 | 150% | Offline, process restarted |
| Landscape/dark | 891×320 | 150% | Offline, process restarted |

All **24 native PNGs** passed actual dimension/nonblank checks and were inspected.
The [capture folders](screenshots/) retain each drawer, profile, points, rank,
match-card and long-name viewport. The rendered-frame wait prevents screenshots
from racing immediate scroll/mode changes. Portrait and narrow captures show full
plots and complete timestamps; landscape captures show scrolled panel portions,
with navigation/reachability established by the passing journey. These are native
viewport images, not full-page composites.

Visual comparison against the approved reference confirmed split identity,
linked names, standing columns, medal tiles, both inline modes, shared match cards
and drawer composition. Long names and footer text wrap; unknown and zero medal
values remain distinct. Fresh Standards and Spec reviews of the drawer and capture
changes found zero implementation findings.

The [machine-readable installed result](installed-results.json) describes this
controlled follow-up on an initialized API 36 software emulator. It does not turn
either failed fresh managed run into a pass, establish a permanent cold-start fix,
or establish a successful empty-cache invocation of `scripts/android-verify`.
Physical phones, API 26 installation and live HTTP acquisition were not tested.

Cleanup completed: the owned emulator, private ADB server and netsimd stopped;
the retained disposable device and both task build directories were removed.
Only the retained evidence directory and reusable managed tool caches remain.
