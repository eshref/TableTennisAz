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
to reach screen-specific states such as the empty duel list. Each journey publishes
its own evidence.
[Changed-file selector](../../scripts/android/pr-journey)

| Changed screens | Journey | Installed test class |
| --- | --- | --- |
| Singles and doubles match details | `match` | `MatchDeviceTest` |
| Player profile and links to history and matches | `profile` | `ProfileDeviceTest` |
| Dedicated singles rating history | `rating-history` | `RatingHistoryDeviceTest` |
| Şəxsi turnirlər, Qoşa turnirlər, tournament results, tournament alerts, and Duellər | `results` | `ResultsDeviceTest` |
| Doubles profile, rating and ranking histories, and linked partners | `doubles-profile` | `DoublesProfileDeviceTest` |
| Rankings, app menu, or shared UI | `native-acceptance` | `NativeAcceptanceDeviceTest` |
| Üz-üzə singles and doubles search and return from match details | `head-to-head` | `HeadToHeadDeviceTest` |

The `head-to-head` journey completes comparisons and retries an unavailable
index in standard light. It then disables networking and force-stops the app
before a second instrumentation run reopens the saved comparisons through
the search form. The evidence contains this second run under
`captures/light-offline/`.

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
The head-to-head journey serves its player directory, index shards, and match
details over HTTPS on emulator loopback in each configuration.

For a pinned doubles profile run, set `app_revision` to the full app commit SHA.
The summary records the app and verifier revisions. To test failure evidence,
enable `fail_after_history_checkpoint`. The light journey fails after the host
validates both history PNGs. The images remain downloadable and the later
configurations show `not-run`.

## Live website check

Choose `fixture_journey: none` and enable `live_site` under **Run workflow**.
Open **live-smoke** to inspect the installed live website journeys on API 26.
This check requires website access from Android. [Android Actions](https://github.com/eshref/TableTennisAz/actions/workflows/android.yml)

After the daily Pages deployment succeeds, dispatch **Android** with
**singles_index_live** to time Əşrəf Səfərov against Etibar Yunusov or
**doubles_index_live** to time Elçin Mustafayev / Erhan Əliyev against
Xəyal Həşimli / Əlixan Əlizadə. Each selected check starts with a fresh
installation on an API 36 emulator with internet access and measures from
**Axtar** to the displayed **Qələbə sayı**. The run summary and corresponding
**live-singles-index** or **live-doubles-index** artifact record the device,
connection, source refresh date, score, and elapsed time. The artifact
includes the result screenshot.

## Live Firebase test alert

Register `az.tabletennis` in a Firebase test project with the Cloud Messaging
API enabled. Put the downloaded `google-services.json` in `app/` and build the
debug APK. Add the test project's service account JSON to the repository's
`FIREBASE_TEST_SERVICE_ACCOUNT` Actions secret. The service account needs
`cloudmessaging.messages.create` and permission to mint its own access token
(`roles/iam.serviceAccountTokenCreator` on the account).

Install the APK on a Google Play compatible phone or a Google APIs emulator.
Open **Turnir bildirişləri** from the app menu, turn the switch on, and grant
notification permission if prompted. Wait for the switch to show that the
subscription is active, then leave the app with Home or Back.

Dispatch **Tournament catalogue** on `main` with a completed singles or doubles
Results index ID in `firebase_test_event_id`. Pick an event whose match results
are on tabletennis.az and whose entry has yet to appear in the live catalogue.
The workflow deploys Pages, confirms the exact catalogue is live, and sends
the newly verified event to `tournament-results-test`. The message uses the
time the workflow confirms that deployment. Tap the Android notification and
verify that the tournament
heading and match results appear. Record the workflow URL, the notification
and results screenshots, and the device or emulator image.
[Tournament catalogue Actions](https://github.com/eshref/TableTennisAz/actions/workflows/tournament-catalogue.yml)

The installed `results` fixture journey exercises opt-in and opens both formats
through the actions on their posted Android notifications while the catalogue
endpoint is unavailable. The
`tournament-alerts-setting`, `singles-alert-results`, and
`doubles-alert-results` checkpoints show the corresponding screens.

## Required check

The **Protect main** ruleset requires **Android installed check**. When
changing the gate, verify an applicable PR with downloadable evidence, an
unrelated PR with a successful applicability result, and a failed installed
check with downloadable partial evidence. [Protect main ruleset](https://github.com/eshref/TableTennisAz/rules/27339222)

## Signed release candidate matrix

After the workflow is merged to `main`, dispatch **Publish Android release**
from `main`. Leave **verify_only** enabled for a verification run. The
protected `android-release` environment provides the production-signed
candidate. Seven parallel jobs each download the
same APK, check its SHA-256, and install it on a KVM-accelerated API 36 emulator.
Each job runs preflight and one saved-fixture journey from the table above
through all four configurations, with the same fixture and restart behavior.
The instrumentation APK is built against the release variant with the debug
signer. The disposable userdebug emulator allows root to start instrumentation
with that signer. A verify-only run can use the latest published version number
on a newer commit and skips publication.
[Release workflow](https://github.com/eshref/TableTennisAz/actions/workflows/android-release.yml)

Open **Summarize signed fixture matrix** for the candidate identity and all
28 display configurations and the additional head-to-head offline restart.
A complete run reports nonzero passing instrumentation and all required PNGs
for each. Download
`android-signed-fixture-matrix` for `verification.md`, validated screenshots
by journey and configuration, and the recorded APK checksums.

Download `android-signed-api36-<journey>` for both installed APKs, the light
checkpoints, progress and test logs, and available failure diagnostics. These
artifacts are retained for seven days, including available evidence from
failed runs. Record the run URL, result, and screenshots when reviewing the
candidate. Live website checks remain available on demand through **Android**.

## Signed in-place upgrade

The same dispatch runs **Verify in-place Android upgrade** in
parallel with the fixture matrix. The job downloads the latest official APK
from GitHub Releases and checks that it and the candidate have the same
application ID and signing certificates. It installs the official APK first,
prepares two dated doubles observations under that version, and opens the pair
profile from the rankings. The test checks both names and the changes from
800 to 858 points and from rank #9 to #2.

On the same emulator, the job replaces the official APK in place with the
checksum-verified candidate. It confirms there is no active network, restarts
the app process, and checks the same UI observations again. Fixture preparation
runs only for the official APK.

Download `android-signed-upgrade` for `verification.md`, the two installed APKs,
their versions and checksums, the phase outcomes in `result.properties`, and
the before and after screenshots under `captures/`. A failed job retains the
available evidence and diagnostics. Record the run URL, result, and screenshots
when reviewing the upgrade. [Release workflow](https://github.com/eshref/TableTennisAz/actions/workflows/android-release.yml)

## Official release evidence

With **verify_only** disabled, publication requires both installed checks
and verifies the collected results and screenshots against the APK SHA-256
and commit. The release publishes the APK and
`TableTennisAz-<version>-verification.zip` together. Open `verification.md` in
the ZIP for the workflow run, candidate identity, and test results. The ZIP
also contains the validated screenshots and recorded phase outcomes. Review
these assets before installing the APK over the official app on the phone.
[GitHub Releases](https://github.com/eshref/TableTennisAz/releases)
