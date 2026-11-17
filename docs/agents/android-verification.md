# Installed Android verification

Use this workflow for Android UI changes and whenever claiming installed-device
verification. The script, version lock and this guide are repository-owned;
global agent instructions and CI configuration are unchanged.

## Run

From the repository root on Linux x86_64:

```sh
scripts/android-verify
```

The command discovers JDK 21 or provisions it, downloads checksum-pinned Android
tools and the API 36 AOSP ATD image, builds both APKs, and verifies preflight.
Full runs then execute the existing host integration suite, lint and device
matrix in isolated build output. Basic Linux prerequisites
are Bash, curl, tar, unzip, flock, timeout, sha256sum and `mkfs.ext4` from
e2fsprogs (with directory population support). The host must provide the shared
libraries required by the Linux emulator. No privileged installation is used.
The downloaded JDK and Android SDK remain subject to their upstream licenses.

It prefers readable/writable `/dev/kvm`; otherwise it uses software CPU emulation.
Phase limits are defined in [limits.properties](../../scripts/android/limits.properties)
and printed by `scripts/android-verify --help`. The preflight total includes setup,
build and at most one automatic environment recovery. Full device checks have a
separate budget. Keep these committed limits unchanged unless the user explicitly
authorizes a different cap. Recovery retains the original deadline and first-attempt logs.
Stop at a failed result and report the failed phase and evidence path. Further
troubleshooting beyond that budget requires the user's explicit authorization.

For a setup-only check:

```sh
scripts/android-verify --preflight-only
```

Preflight still installs and runs the app. It checks both saved match scores,
window focus, nonblank installed screenshots, and their actual 412×891 dimensions.
Its shortened match checks establish readiness; the complete navigation and
recreation journeys run only after preflight passes. Zero executed tests do not
pass verification.

## Full checks and evidence

The default full run exercises `MatchDeviceTest` in four configurations:

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

The same preflight can precede other existing installed test classes:

```sh
scripts/android-verify --tests az.tabletennis.ProfileDeviceTest
```

Select the journey that covers the changed screens: `MatchDeviceTest`,
`ProfileDeviceTest`, `RatingHistoryDeviceTest` or `ResultsDeviceTest`. Run classes
separately because they prepare different saved fixtures. The default match
journey does not establish visual acceptance for an unrelated screen.

Selected tests must capture screenshots using the supplied `capturePrefix`.
Match, profile and rating-history journeys support `reuseSaved`; the results
journey prepares its saved fixtures for each configuration, so it establishes
offline rendering rather than reuse across processes. Match-specific offline
comparisons apply to the default match matrix. Live website tests have separate
setup needs and are not suitable for this saved-fixture matrix.

The command prints an evidence directory. Read `result.properties`: completion
requires `status=passed`, `preflight=passed`, `cleanup=complete`, and, for full
runs, `matrix=passed`. Inspect the PNGs against the approved reference before
claiming visual acceptance. Automated nonblank/dimension checks establish that
a capture exists; visual review establishes its composition and readability.

Evidence includes APKs, their checksum, screenshots, captured UI properties,
runner logs, host-test XML and the result summary. Keep relevant evidence with
the task; commit screenshots when required by its acceptance criteria. Historical
feature results remain in `docs/android-verification.md`.

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

An initial **system** ANR dialog can steal focus during slow startup. The runner
can select its Wait action. Application assertion failures remain failures.
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

On 2026-11-17, both APKs built, all 160 host tests and lint passed, and the
launcher/provisioner syntax and Java compilation passed. Interruption cleanup
completed and retained its result without truncation.

The approved extended measurement window ended without a passing installed
preflight on this host, which has no KVM and uses a Windows-backed workspace.
ADB confirmed the first-boot timeout and drawing settings, but Android framework
readiness remained incomplete. APK installation, screenshots and the full matrix
remain unverified for this workflow. The original preflight cap is retained;
there is no successful measurement supporting an increase. See the concise
[validation record](../verification/android-workflow/results.properties) for
the evidence locations and measured checks.
