## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Opening a singles or doubles match shows a clear native scoreboard with grouped
sides, actual scores, supplied event/date/time information, and linked player
identities with the shared portrait/fallback treatment. Every participant can
be followed to their profile and Back restores the originating journey.

## Acceptance criteria

- [ ] Use the approved detail chrome and scoreboard composition, with participants grouped on their actual sides and scores clearly separated. Winner emphasis must agree with the recorded final score and remain understandable without color alone.
- [ ] Preserve two participants for singles and all four for doubles, each linked to their correct player profile, including same-named participants and professional players reached outside amateur rankings.
- [ ] Use optional available participant photo metadata/cached portraits with the shared asynchronous loader and silhouette fallback. Photo failure must not prevent match details or player links from being usable; do not introduce per-ranking-player full-profile fetches to support this screen.
- [ ] Show source-supplied event, date, and time when present; do not invent missing times, participant identities, or match outcomes. Preserve existing saved match compatibility and source validation.
- [ ] Back returns through the match and the originating player history, pair history, or Results selection without losing context. Existing callers do not need their later visual redesign to reach this screen.
- [ ] Present loading, initial failure/retry, saved refresh failure, offline, and success states appropriately while preserving usable saved details and successful-update times.
- [ ] Verify singles/doubles participant and score attribution, profile links/Back, photo independence, and offline match reuse through the existing native-UI/real-HTTP match journeys, retaining their distinct combined-workflow guarantees.
- [ ] Check long names, all participant links, accessible scores/actions, large text, both themes, and narrow/landscape layouts. Capture the native match-details screen against the approved reference and keep relevant checks green.
