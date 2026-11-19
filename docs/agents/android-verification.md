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

| Changed screens | Journey | Local test class |
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

### Full matrix on demand

Choose **Run workflow** in Android Actions, select the branch, and choose a
`fixture_journey` from the table. This runs the four configurations described
below. Use `none` for host checks and `live_site` for the opt-in website check.
[Android Actions](https://github.com/eshref/TableTennisAz/actions/workflows/android.yml)

For the doubles profile pilot, set `app_revision` to pin the app and fixtures.
The merged build containing #52 is `314238d1a982f55432bf4b47d2fd5f87304342e9`.
The workflow records the pinned app revision and the revision supplying the
verifier and doubles profile test. The `fail_after_history_checkpoint` option
deliberately fails after the host validates the two history PNGs. The images
remain downloadable and the three later configurations show `not-run`.
[Pilot pass 1](https://github.com/eshref/TableTennisAz/actions/runs/44463512013)
[Pilot pass 2](https://github.com/eshref/TableTennisAz/actions/runs/44463976826)
[Pilot pass 3](https://github.com/eshref/TableTennisAz/actions/runs/44464322734)
[Pilot failure](https://github.com/eshref/TableTennisAz/actions/runs/44464707062)

### Required check rollout

Verify the applicable, unrelated, and failed PR cases on GitHub Actions before
adding **Android installed check** from the GitHub Actions app to the existing
**Protect main** ruleset. For the applicable run, check the journey, validated
PNGs, summary and APK checksums in the seven-day artifact. For the unrelated run,
check the successful applicability summary and skipped emulator setup. For the
failed run, check the failed job conclusion and download its partial evidence.
[Protect main ruleset](https://github.com/eshref/TableTennisAz/rules/27339222)

## Local troubleshooting

Start with preflight from the repository root on Linux x86_64:

```sh
scripts/android-verify --preflight-only
```

The command discovers JDK 21 or provisions it, downloads checksum-pinned Android
tools and the API 36 AOSP ATD image, builds both APKs, and verifies preflight.
Full runs then execute the device matrix, stop the owned emulator, and run the
existing host integration suite and lint in isolated build output. These checks
share one full-phase deadline. Separating the emulator and Robolectric avoids
memory pressure on 8 GiB hosts. Basic Linux prerequisites
are Bash, curl, tar, unzip, flock, timeout, sha256sum and `mkfs.ext4` from
e2fsprogs (with directory population support). The host must provide the shared
libraries required by the Linux emulator. No privileged installation is used.
The downloaded JDK and Android SDK remain subject to their upstream licenses.

It prefers readable/writable `/dev/kvm`; otherwise it uses software CPU emulation.
Preflight has a 60-minute budget for setup, build and at most one automatic
environment recovery. Full device checks have a separate 45-minute budget.
`scripts/android-verify --help` prints the configured limits. Keep these
committed limits unchanged unless the user explicitly
authorizes a different cap. Recovery retains the original deadline and first-attempt logs.
Stop at a failed result and report the failed phase and evidence path. Further
troubleshooting beyond that budget requires the user's explicit authorization.
Let software preflight run before other builds or test suites; the command
deliberately defers host checks until installed readiness is established.
Evidence is written to the printed `.android-verification/evidence/run-*` path.
[Phase limits](../../scripts/android/limits.properties)

Preflight still installs and runs the app. It checks both saved match scores,
window focus, nonblank installed screenshots, and their actual 412×891 dimensions.
Its shortened match checks establish readiness; the complete navigation and
recreation journeys run only after preflight passes. Zero executed tests do not
pass verification.

## Full checks and evidence

The default full run (`scripts/android-verify`) exercises `MatchDeviceTest` in
four configurations:

1. Standard light at 412×891.
2. Dark at 412×891 after disabling networking and restarting the app process,
   reusing saved data.
3. Light at 320×568 and 150% text.
4. Dark at 891×320 and 150% text.

Each complete match journey checks scores, every participant profile link,
Android Back and Activity recreation. The runner verifies screenshot dimensions
for each configuration. It confirms Android reports no default network before
offline tests, and compares visible scores/update labels, the offline notice
and disabled Refresh against the light capture. These assertions use installed
public view outputs; private stored files only prepare fixtures.

Use the class from the journey table to reproduce the focused PR check or to run
its full matrix locally:

```sh
scripts/android-verify --focused --tests az.tabletennis.ProfileDeviceTest
scripts/android-verify --tests az.tabletennis.ProfileDeviceTest
```

Run classes separately because they prepare different saved fixtures.

Selected tests must capture screenshots using the supplied `capturePrefix`.
Match, profile, doubles-profile, rating-history and combined acceptance journeys
support `reuseSaved`; the results
journey prepares its saved fixtures for each configuration, so it establishes
offline rendering rather than reuse across processes. Match-specific offline
comparisons apply to the default match matrix. Live website tests have separate
setup needs and are not suitable for this saved-fixture matrix.

Run the existing opt-in live website journeys separately:

```sh
scripts/android-verify --live
```

This uses the same installed preflight, then runs `LiveRankingsTest`,
`LiveExperienceTest` and `LiveDoublesTest` sequentially online in the disposable
guest. The runner enables Wi-Fi, requests a join to the emulator's `AndroidWifi`
access point if needed, and waits for an active Android default network.
Network setup and instrumentation share the full-phase deadline. The runner
stops on the first failed journey and records which classes passed. Use those
results to verify Android's production HTTPS requests.

Open `result.properties` in the printed evidence directory. A completed run has
`status=passed`, `preflight=passed` and `cleanup=complete`. Look for
`focused=passed`, `matrix=passed`, or `liveSource=passed` for the chosen run. Inspect the
PNGs against the approved reference before claiming visual acceptance.
Automated nonblank/dimension checks establish that
a capture exists; visual review establishes its composition and readability.
`checksFinished` records when checking ended; `finished` also includes process
and file cleanup. The phase watchdog stops when cleanup begins, so slow file
deletion cannot overwrite a completed check result. All verification operations
remain bounded by their phase deadline.

Evidence includes APKs, their checksum, screenshots, captured UI properties,
runner logs, host-test XML and the result summary. Keep relevant evidence with
the task; commit screenshots when required by its acceptance criteria. Historical
feature results remain in `docs/android-verification.md`.

On a guest failure, the runner attempts bounded evidence collection during
cleanup. Inspect `failure-anr.log` for
the start of the newest Android ANR thread dump. Check its timestamp and process
against the failure. Window and logcat diagnostics may also be available. An
incomplete fixture run can save unvalidated images under `failure-captures/`.

API 26 remains covered by existing host tests. Report installed coverage as API
36 emulator coverage. Physical-phone behavior, API 26 installation and live HTTP
journeys each need their own explicitly scoped verification.

## Isolation, cache and cleanup

Downloads, images and evidence default to the ignored `.android-verification/`
directory. Smaller SDK packages use a managed native Linux cache under `/tmp`
when space permits; the repository cache records its path and ownership. This
avoids slow metadata operations on Windows-backed workspaces. Caches are reusable;
every invocation owns its emulator, private ADB server,
ports, user directory, disk and Gradle output. Existing emulators and ADB servers
are left running. Tool installation uses a cache lock. The cache/device filesystem
needs at least 12 GiB free for cold downloads, image extraction and the temporary
6 GiB data disk; choose a larger filesystem when necessary.
Blank disk formatting uses a sparse file on Linux `/tmp` (at least 256 MiB free),
then converts it into the run's thin image. This avoids scanning a dense 6 GiB
file through a Windows-backed filesystem. Each run still gets fresh data.

Environment overrides:

| Variable | Purpose |
| --- | --- |
| `ANDROID_VERIFY_CACHE` | Reusable SDK and archive cache |
| `ANDROID_VERIFY_OUTPUT` | Parent for small per-run evidence directories and disposable devices |
| `ANDROID_VERIFY_JDK` | An explicit JDK 21 directory, or `pinned` to provision the locked JDK |
| `ANDROID_VERIFY_BUILD_ROOT` | Parent for disposable Gradle output; defaults to `/tmp` when space permits |

On success, failure or interruption, the runner stops its own processes and
removes its device/build directories while retaining evidence and cached tools.
Use `--retain-device` only when a full disk is needed for diagnosis; the result
records its retained path. Remove that owned directory after diagnosis.

## Known device settings

The pinned ATD image disables drawing by default. The fresh data image supplies
drawing and hardware-timeout settings through `local.prop` before first boot.
The runner verifies them over ADB. If the guest did not load the timeout setting,
it applies the runtime fallback and restarts zygote. These changes apply to the
disposable guest, never to application configuration.

Boot completion can precede the end of background package optimization. Software
APK installation uses the remaining preflight budget, avoiding a separate
three-minute cutoff while preserving the total limit.

An initial **system** ANR dialog can steal focus during slow startup. The runner
can select its Wait action. Application assertion failures remain failures.

Software verification sets the disposable guest's long-press threshold to
10 seconds and records the verified value. On this image, slow input injection
has held an ordinary Espresso tap for 2.580 seconds, activating a button tooltip
instead of its click. This setting changes only the disposable guest; app and
phone settings and phase budgets remain unchanged. Accelerated runs keep their
normal threshold. Long-press behavior needs its own verification.

Blank PNGs, missing focus and wrong dimensions require a failed preflight; a host
render or successful install command alone cannot establish installed acceptance.

This image can ignore user-rotation settings. Landscape uses an explicit
`wm size 891x320`, with captured dimensions checked. Network-disable commands can
also return before connectivity disappears; the runner checks the actual default
network before starting a new offline app process.

Tool URLs and checksums live in `scripts/android/tools.properties`. Update that
lock deliberately, then verify the installed product scenarios from an empty SDK
cache before calling the new configuration supported.

## Current validation

On 2026-11-17, the fresh API 36 software run passed both installed preflight
cases and eight full participant/Back/recreation cases across all four
configurations. All 160 host tests passed without failures or skips; lint
reported zero errors and 26 warnings. Preflight took about 31 minutes 42 seconds
using cached pinned tools on a Windows-backed workspace without KVM.

The command exhausted its 30-minute full-phase budget while collecting the last
captures, after all installed cases had passed. Its overall result remains
`failed`; cleanup completed. Local inspection afterward verified all ten retained
PNGs, their dimensions and nonblank content, plus visible scores, unchanged
update labels, the offline notice and disabled Refresh across all reused
configurations. The landscape captures show the initial metadata viewport;
passing journeys establish that participants below it remain reachable.

The [screenshots](../verification/android-workflow/screenshots/) and
[validation record](../verification/android-workflow/results.properties)
distinguish functional verification from the command's incomplete orchestration.
The approved caps are now 60 minutes for preflight and 45 minutes for the full
phase, allowing time for collection and host variation. The recorded run used
the earlier 30-minute full-phase cap; the suite was not rerun for this
configuration-only increase.
Empty-cache provisioning, KVM and physical-device execution were not rerun.

Software preflight requests Android's `speed` compiler filter for the app and
instrumentation primary bytecode inside its existing budget. Both requests
succeeded in about 78 seconds. The ART snapshot after preflight reported `verify`
for the test APK; native AOT speedup is not established. The command is supported
by the [Android 16 ART shell implementation](https://android.googlesource.com/platform/art/+/refs/tags/android-16.0.0_r1/libartservice/service/java/com/android/server/art/ArtShellCommand.java).
