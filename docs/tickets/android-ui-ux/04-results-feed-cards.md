## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Players use the Results destination as a readable partial results feed, with
clear type/event filters and structured match cards separating participants,
scores, date/time metadata, and event identity. Existing match-detail links and
bounded Load more remain available.

Introduce the reusable match-card presentation through this working results
journey so profiles can adopt the same cards in their later slices.

## Acceptance criteria

- [ ] Results opens on its existing loaded feed without requiring event selection. All results, event matches, duels, and event selection remain clear and usable in the approved design.
- [ ] Match cards separate both sides' names, final scores, match type, source date/time where available, and event metadata. Full doubles names wrap readably and any winner emphasis agrees with the recorded outcome.
- [ ] Cards open the correct existing match detail and retain the originating Results context when Back returns. The detail screen does not need its later visual redesign to make this journey functional.
- [ ] Preserve event choice outside the initial general batch, per-selection saved snapshots, bounded loading and saved cursors, mixed-source ordering, overlap deduplication, and atomic failure behavior. Load more does not falsely refresh older content's update time.
- [ ] Keep partial-coverage messaging readable but secondary. Never imply global completeness, invent within-day chronology, or present an unseen offline selection as verified empty.
- [ ] Present appropriate loading, verified empty selection, first-load failure/retry, saved refresh failure, offline, and success feedback while preserving usable content and timestamps.
- [ ] Expose shared match-card presentation through a small reusable interface inside this slice; do not create duplicate variants for each later profile screen or redesign participant data contracts unnecessarily.
- [ ] Verify the affected filter/card/navigation/pagination/state journeys through existing native-UI/HTTP-fixture integration seams, reusing compatible existing guarantees and extending only distinct gaps.
- [ ] Check filter reachability, accessible scores/names, full long names, large text, both themes, and narrow/landscape layouts. Capture native Results against the approved reference and keep relevant checks green.
