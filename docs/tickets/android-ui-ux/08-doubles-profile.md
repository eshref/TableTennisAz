## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Opening a doubles partnership shows its split-photo identity, both linked
players, current saved pair standing, partnership medals, existing inline
Xal/Mövqe history, and consistent paginated match cards. Players can explore
either partner or a match and return without losing the partnership context.

## Acceptance criteria

- [ ] Present the selected partnership using the shared split circular avatar, independently falling back per partner, with both full names linked to the correct player profiles.
- [ ] Current pair points and published position come from the latest successfully saved doubles ranking with its own update time. Preserve distinct positions for tied points and distinct partnerships sharing a player.
- [ ] Show the pair's own gold/silver/bronze medal counts, preserve unknown versus zero, refresh mutable counts under the existing rules, and never sum individual-player medals. Metadata failure preserves usable saved counts and results.
- [ ] Keep the existing inline Xal/Mövqe controls and improve chart/observation labels in both themes. Preserve actual timestamps, chronological spacing, better ranks higher on the chart, first-point/no-history explanations, and accessible values.
- [ ] Switching history modes, opening this profile, and refreshing medals/matches do not collect or backfill ranking observations. Retain selected mode through supported refresh/recreation behavior and show device-local coverage honestly.
- [ ] Reuse the shared Results match cards for the pair's tournament matches and duels, maintaining pagination, coverage messages, selected-pair attribution, and correct match links.
- [ ] Back navigation through individual profiles or match details restores the pair profile and the originating Doubles ranking selection/search/scroll context.
- [ ] Preserve legacy identities/results/rankings without optional metadata or observations, saved-data-first rendering, offline histories/photos, the refresh threshold, atomic failure behavior, and accurate separate update times.
- [ ] Verify the affected partnership identity, split photos/fallbacks, standing/medals, inline chart modes, pagination, navigation, and offline lifecycle through existing native-UI/real-HTTP doubles workflows. Preserve their combined observation/movement guarantees and extend only distinct gaps.
- [ ] Check long partner names, chart/medal readability, accessible values/actions, large text, both themes, and narrow/landscape layouts. Capture the native Doubles profile against the approved reference and keep relevant checks green.
