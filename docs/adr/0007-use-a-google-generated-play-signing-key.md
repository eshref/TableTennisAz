# Use a Google-generated signing key for Google Play

**Superseded by [ADR 0008](0008-preserve-github-installs-on-google-play.md).**

Treat the first Google Play release as a fresh install, including on the
owner’s phone. Use a Google-generated app signing key so Google manages the key
that signs the app delivered through Play. Users who installed the GitHub APK
must uninstall it before installing the Play version. Uninstalling clears their
on-device doubles rating history and ranking history.

The in-place upgrade requirement in [ADR 0006](0006-verify-signed-android-releases.md)
applies to updates between GitHub APKs.
