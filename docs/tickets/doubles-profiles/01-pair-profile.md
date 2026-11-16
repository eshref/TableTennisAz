## Parent

Part of [spec #16](https://github.com/eshref/TableTennisAz/issues/16).

## What to build

Selecting a pair in Doubles rankings opens a doubles profile showing both partners, current published pair points and position, the pair's gold/silver/bronze medal counts, and its latest matches. Preserve both player-profile links, match-detail navigation, pagination, saved-data behavior, and restoration of the user's ranking context.

This independently usable slice delivers the profile shell and its live/saved metadata. The embedded history chart is delivered by the dependent chart ticket. Reuse the existing pair-results destination, website integration, and saved-screen behavior; no prerequisite refactor is needed.

## Acceptance criteria

- [ ] Selecting a doubles ranking row opens the selected pair's profile with both full names and links to the correct individual player profiles.
- [ ] Current pair points and position come from the latest successfully saved doubles ranking. Preserve published positions even when points tie; do not apply amateur singles ranking rules.
- [ ] Display separate gold/silver/bronze counts from the pair metadata interface, validated against the selected partnership. Never sum the two individual players' medals.
- [ ] Refresh mutable medal counts according to the existing freshness policy even when the pair's identity is cached. Valid zero counts display as zero; absent information is distinguishable from zero. A failed metadata refresh preserves previously saved counts.
- [ ] Retain the specific partnership's tournament matches and doubles duels, newest-first ordering, Load more, disabled pagination semantics, overlapping-result deduplication, and links to match details. Partnerships sharing a player stay distinct.
- [ ] Returning from match details or either individual profile restores the pair profile; returning to Rankings preserves Doubles selection, search text, and scroll position.
- [ ] Show saved profile data immediately with its successful update time. Retain it offline and after refresh failure. Reopening online refreshes data over 15 minutes old; manual refresh remains available online.
- [ ] Previously saved pair identities, rankings, and results remain usable after the update even though they lack new medal fields. Do not discard saved results to add the profile.
- [ ] Keep native Azerbaijani headings, medal descriptions, status messages, and accessibility labels. Existing singles behavior remains unchanged.
- [ ] Extend existing Android UI integration scenarios using the real local HTTP fixture server, production parsing, controlled clock, and durable storage. Demonstrate correct pair points/rank/medals, a medal update despite cached identity, missing versus zero counts, and preservation after metadata failure.
- [ ] Reuse existing match/player navigation, pagination, freshness, and offline tests where they already establish the required guarantees; extend them only for distinct new profile behavior. Verify saved profile information after restart through visible outputs.
- [ ] Adapt the existing opt-in live doubles journey to open a real pair profile and verify ranking/metadata/results compatibility without asserting fixed live values.

## Blocked by

None (can start immediately).
