---
name: create-release
description: Prepare, verify, and publish a signed TableTennisAz Android release on GitHub Releases. Use for release version and notes, signed verification, or publication and recovery.
---

# Create a TableTennisAz release

## Establish the release

1. Identify the requested outcome: preparation, signed verification, or publication. Read
   the [release guide](../../../README.md#preparing-an-android-release) and use the GitHub
   repository and authentication guidance in the [issue tracker guide](../../../docs/agents/issue-tracker.md).
2. Identify the intended commit and version. For a new release, compare remote `main` with the
   latest official release tag and read `version.properties` and `CHANGELOG.md`. Check for a
   matching release PR, draft, or workflow run. Match runs by workflow, ref, commit, and mode.
   Resume matching work at its current step. Before publication, check for unfinished official
   drafts across versions and resolve conflicting versions, tags, or commits.
3. When preparing a new version, recommend the public version from the release guide, derive
   the next Android version code from the latest official release, and summarize user-visible
   changes since its tag. If publication depends on new public tournament catalogue data,
   check the live data during preflight to determine whether to include a deployment in the
   request for approval.
4. After read-only preflight, ask once for any missing decisions and permissions within the
   requested scope. Name the branch to push and the PR target where applicable. Include any
   intended merge, configured review bypass, workflow dispatches, and publication. Carry
   forward approvals already given. Obtain explicit authorization before pushing.

## Prepare and merge

- For a new version, prepare a branch from current `main`, normally `release/android-v<version>`.
  Update `version.properties` and the dated section in `CHANGELOG.md` together. Use the tag
  comparison to recover user-visible notes when Unreleased is empty. Validate with
  `scripts/android/test-release-metadata` and run `scripts/android/ReleaseMetadata.java` against
  the previous tag's `version.properties` to check the actual version and extracted notes.
- With authorization, push the branch and open a release PR against `main`. If merging is
  in scope, wait for the required checks and match the tested PR head when merging. Use the
  owner's configured review bypass only when explicitly authorized and the required checks
  have passed. Confirm the merged release commit on remote `main`.

## Check the live catalogue when the release depends on it

- Read the [catalogue ADR](../../../docs/adr/0009-publish-tournament-catalogue-on-github-pages.md)
  and check the live URL configured by `RankingsApplication.tournamentCatalogueUrl()`.
  Confirm the catalogue has the data the release needs. Published podiums require populated
  podium data for the applicable formats. New-tournament dots require the `firstListed` history.
- If required data is missing, monitor an applicable
  [Tournament catalogue](../../../.github/workflows/tournament-catalogue.yml) run, or dispatch it
  from `main` when authorized. Verify both successful Pages deployment and the data at the
  app's public URL before starting publication.

## Run signed verification or publication

- Resume a matching run if one exists. When a new run is needed and authorized, confirm
  remote `main` still points to the intended commit and dispatch the
  [Publish Android release workflow](../../../.github/workflows/android-release.yml) from
  `main`. For signed verification only, set `verify_only=true`. This can use the latest
  published version on a newer commit. For publication, use one dispatch with
  `verify_only=false`. Publication requires host checks, all 24 signed fixture configurations,
  and the in-place upgrade from the previous official APK.
- Confirm the run's revision matches the intended commit. If a run you dispatched uses a
  different revision, cancel it promptly and check `main` again. Monitor the intended run
  through its terminal result. Use the
  [Android verification guide](../../../docs/agents/android-verification.md#signed-release-candidate-matrix)
  for evidence and screenshots. Diagnose failures from the failing job and evidence. For
  recoverable publication failures, follow the
  [release guide's retry procedure](../../../README.md#publish-and-verify) on the original run
  and preserve matching draft assets. Report a specific blocker if recovery needs additional
  authorization or a different revision.
- For verification only, report the candidate commit, workflow URL, fixture matrix result,
  upgrade result, and evidence links. For publication, verify that the public `v<version>`
  tag points to the tested commit and that both the signed APK and verification ZIP download
  successfully. Check the published asset checksums. Match the APK checksum and commit to
  `verification.md`, the APK version name and code to the release metadata, and its signing
  certificate to the recorded production fingerprint. Report the release, asset, and workflow
  links, and the catalogue deployment link if applicable.
