# Preserve GitHub installs when updating through Google Play

The Google Play release must update the existing GitHub APK in place and keep
the doubles rating history and ranking history saved on the phone. Keep the
application ID `az.tabletennis` and enroll the existing production signing key
in Play App Signing. Use a separate upload key for the Android App Bundle.
[Android app signing guide](https://developer.android.com/studio/publish/app-signing)

Give the Play release a version code higher than the installed GitHub APK.
Before publishing to production, install the update through a Play test track
over the signed GitHub APK and verify both saved histories after the update.
The GitHub upgrade scenario provides the baseline for these checks.
[Installed Android verification guide](../agents/android-verification.md#signed-in-place-upgrade)
