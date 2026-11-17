# Agent instructions

## Agent skills

### Issue tracker

Issues and specs use GitHub Issues. Before tracker operations, read `docs/agents/issue-tracker.md`.

### Triage labels

Before classifying issues or applying triage labels, read `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `GLOSSARY.md` and `docs/adr/`. Before exploring domain behavior, read `docs/agents/domain.md`.

### Installed Android verification

For Android UI changes or installed-device verification claims, follow `docs/agents/android-verification.md` and run `scripts/android-verify`. Report its result and evidence path. A passing installed preflight is required before full device checks; respect the documented time limit when it fails.
