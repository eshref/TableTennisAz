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
acceptance remains open below.

## Installed preflight failed — native visual acceptance remains open

On 2026-11-17, before host builds/tests, ran:

```sh
scripts/android-verify --preflight-only
```

This initial readiness check built the unchanged starting application. API 36
software emulation required the runner's one automatic boot recovery. Both APKs
installed and bytecode compilation succeeded, but the singles smoke case failed
with `IllegalStateException: Cannot save screenshots`. The command then reached
its 12-minute instrumentation timeout after the doubles case started.

The overall attempt lasted about 42 minutes. Its result is `status=failed`,
`phase=preflight`, `cleanup=complete`; there is no passing installed preflight.
The [result properties](preflight.properties) and [instrumentation output](preflight-tests.txt)
are retained here. Full local evidence remains at
`.android-verification/evidence/run-20261117T171014Z-1eJg7J/`.
The APK checksum in that preflight record belongs to the starting build.

Stopped installed verification at this failed result, following the
[repository guide](../../agents/android-verification.md). No further emulator
troubleshooting, full device matrix, new pair-profile installed journey or
native screenshot comparison was performed. Physical phones, API 26 installation
and the live-source journey were not tested.

The approved reference is `docs/prototypes/android-final-ui-prototype.html` at
`8cd6a1999d6a4522332249e7a56175989b08655a`. Its default pair's public portraits,
names, points, published position and medals are represented in the new installed
fixture for a comparable future capture. The assets are test-only; fixture
matches and observations do not become production data.

After authorized environment follow-up, run:

```sh
scripts/android-verify --tests az.tabletennis.DoublesProfileDeviceTest
```

Require passed preflight, matrix and overall status plus complete cleanup, then
inspect the actual `issue31` PNGs against the approved reference. Until then,
native visual acceptance for this issue is incomplete.
