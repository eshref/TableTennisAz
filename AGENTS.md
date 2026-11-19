# Agent instructions

## Agent skills

### Issue tracker

Issues and specs use GitHub Issues. Before tracker operations, read `docs/agents/issue-tracker.md`.

### Triage labels

Before classifying issues or applying triage labels, read `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `GLOSSARY.md` and `docs/adr/`. Before exploring domain behavior, read `docs/agents/domain.md`.

### Installed Android verification

For Android UI changes or installed-device verification claims, start with the GitHub Actions workflow in [the Android verification guide](docs/agents/android-verification.md). Confirm the selected journey covers the changed screens and report the run URL, result, and evidence. For local troubleshooting, run the documented preflight and respect its time limit.
