# Verify the signed Android APK before publishing

The issue #32 emulator matrix passed on an earlier APK. The final APK timed out before installation. Release verification will install the exact production-signed APK that GitHub Releases will publish, so the screenshots and test results apply to the build the owner installs. [Issue #32 verification](../verification/issue32/README.md)

## Release gate

Run every supported saved-fixture journey on KVM-accelerated standard GitHub-hosted Ubuntu runners, using API 36 in all four configurations defined in the verification guide. Run an in-place upgrade from the previous official APK and check that the same saved history is visible offline after the update. Publish only after the installed tests and screenshot collection pass. Live website tests remain available on demand. [Android verification guide](../agents/android-verification.md)

The owner's phone runs the official production app and receives manual updates from GitHub Releases. Preserve the application ID and production signing identity so Android can install each update in place. Keep the final emulator screenshots and verification summary with the published APK.

## Pull requests and evidence

Require a passing installed journey for the changed screens on UI pull requests. Keep the full four-configuration matrix available on demand. Before making the PR check mandatory, run the `DoublesProfileDeviceTest` matrix three consecutive times on the same merged build. Verify that an intentionally failed later check still leaves the first screenshot downloadable.

Capture the changed screen in light mode first and copy its PNGs to the host before continuing. Publish available captures and diagnostics even if a later check fails. Include the exact app APK with PR evidence and retain PR artifacts for seven days. Record the commit SHA, app and test APK checksums, Android version, test configuration, and outcome. Record each phase as it progresses so another agent session can resume monitoring at the same GitHub Actions run URL.

## Installed verification workflow

Use GitHub Actions for installed verification. KVM acceleration keeps preflight and device checks practical, and Actions keeps the tested APK and available evidence together at the run URL. [Android verification guide](../agents/android-verification.md)
