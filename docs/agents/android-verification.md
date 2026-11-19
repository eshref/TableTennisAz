# Installed Android verification

## GitHub Actions

Open **Android installed check** from the pull request's **Checks** tab. Keep
the run URL. The summary shows the journeys selected from the PR's changes.
Each selected journey runs in standard light after installed preflight on an
accelerated API 36 emulator. Unrelated PRs receive a successful applicability
result before emulator setup. Host integration tests, lint, and the debug build
have their own check. [Android Actions](https://github.com/eshref/TableTennisAz/actions/workflows/android.yml)

Confirm the selected journeys reach the changed screens. The selector uses a
focused journey for each affected screen it recognizes. Shared code and rankings
also select the combined journey. Shared string resources select all journeys
to reach screen-specific states such as the Duels filter. Each journey publishes
its own evidence.
[Changed-file selector](../../scripts/android/pr-journey)

| Changed screens | Journey | Installed test class |
| --- | --- | --- |
| Singles and doubles match details | `match` | `MatchDeviceTest` |
| Player profile and links to history and matches | `profile` | `ProfileDeviceTest` |
| Dedicated singles rating history | `rating-history` | `RatingHistoryDeviceTest` |
| Results, filters, and event selection | `results` | `ResultsDeviceTest` |
| Doubles profile, rating and ranking histories, and linked partners | `doubles-profile` | `DoublesProfileDeviceTest` |
| Rankings, app menu, or shared UI | `native-acceptance` | `NativeAcceptanceDeviceTest` |

On the run page, open **Run installed preflight and saved-fixture journey** for
live progress. Download `android-api36-<journey>` from **Artifacts** on the same
page, including after failure. The archive contains the exact installed
`app-debug.apk` and test APK when built, `verification.md`, `progress.log`, and
the per-run `result.properties` with both APK checksums and phase outcomes.
It is retained for seven days. Inspect the PNGs in `checkpoints/light/`, where
the host validates required light captures as the test reaches them. Completed
configurations have validated PNGs under `captures/`. A failed run can also
contain diagnostics and unvalidated guest files under `failure-captures/`.
A required screenshot or instrumentation failure fails the check.

### Diagnose a failed run

Open `verification.md` and the run's `result.properties` to identify the failed
phase. Preflight runs the installed singles and doubles match score checks and
verifies window focus and nonblank 412×891 screenshots. Instrumentation and
screenshot collection each have their own result. For a failed journey, inspect
`tests-light.log` or the configuration's test log. When present, compare the
timestamp and process in `failure-anr.log` with the failure and check the window
and logcat diagnostics.

For visual acceptance, inspect the validated screenshots for the changed
screens. Report the run URL, selected journeys, result and evidence.

## Full matrix on demand

Choose **Run workflow** in Android Actions, select the branch, and choose a
`fixture_journey` from the table. Each journey runs after preflight in four
configurations. [Android Actions](https://github.com/eshref/TableTennisAz/actions/workflows/android.yml)

| Configuration | Viewport | Text scale |
| --- | --- | --- |
| Standard light | 412×891 | 100% |
| Dark, offline | 412×891 | 100% |
| Narrow light | 320×568 | 150% |
| Landscape dark | 891×320 | 150% |

After standard light, the runner disables networking and restarts the app
process. The match, profile, doubles profile, rating history, and combined
journeys reuse saved data. The Results journey prepares its saved fixtures
for each configuration to check offline rendering.

For a pinned doubles profile run, set `app_revision` to the full app commit SHA.
The summary records the app and verifier revisions. To test failure evidence,
enable `fail_after_history_checkpoint`. The light journey fails after the host
validates both history PNGs. The images remain downloadable and the later
configurations show `not-run`.

## Live website check

Choose `fixture_journey: none` and enable `live_site` under **Run workflow**.
Open **live-smoke** to inspect the installed live website journeys on API 26.
This check requires website access from Android. [Android Actions](https://github.com/eshref/TableTennisAz/actions/workflows/android.yml)

## Required check

The **Protect main** ruleset requires **Android installed check**. When
changing the gate, verify an applicable PR with downloadable evidence, an
unrelated PR with a successful applicability result, and a failed installed
check with downloadable partial evidence. [Protect main ruleset](https://github.com/eshref/TableTennisAz/rules/27339222)
