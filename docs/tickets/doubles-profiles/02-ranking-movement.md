## Parent

Part of [spec #16](https://github.com/eshref/TableTennisAz/issues/16).

## What to build

After a successful Doubles ranking refresh, show each pair's movement since the previous successful refresh saved on that device: a small green up arrow with places gained or a red down arrow with places lost. Show no indicator for unchanged or unavailable comparisons.

Save complete dated ranking observations, including every pair's points and published position, as part of the same successful-refresh lifecycle. These observations support the movement comparison now and provide the history source for the dependent chart ticket. This slice is demoable in the existing ranking screen without the new pair profile.

## Acceptance criteria

- [ ] Every complete, validated, successfully saved doubles ranking refresh records each pair's stable identity, observation time, published points, and published position. Capture all pairs, including those whose profiles have never been opened.
- [ ] Preserve prior observations as later refreshes succeed. Use the existing app clock and retain enough time information to order multiple refreshes on one date. Do not fabricate observations for earlier periods.
- [ ] Compare the selected pair's new published position with its position in the immediately preceding successful saved ranking. Preserve website positions despite tied points.
- [ ] Moving #12 to #9 displays a small green upward arrow and 3. Moving #9 to #11 displays a small red downward arrow and 2. Include accessible descriptions of direction and places moved in Azerbaijani.
- [ ] Display no arrow, count, dash, or zero when the position is unchanged, on the first saved observation before a comparison is available, or when the pair was absent from the immediately previous saved ranking.
- [ ] If a pair disappears and later returns, do not use an older position as a substitute for the missing immediately previous position. Retain actual earlier observations for its future chart.
- [ ] Searching by either partner changes row visibility only; published position and movement remain the same.
- [ ] Reopening Rankings, recreating its screen, or reading saved data neither creates an observation nor recomputes movement against itself. Retain the latest saved movement through restart and offline access.
- [ ] Each later successful refresh replaces the movement comparison. A successful refresh with unchanged positions clears earlier movement indicators.
- [ ] Network, parsing, identity-resolution, validation, or persistence failure preserves the last successful ranking, observations, movement, and update time. A partial download or interrupted name-resolution step must not appear as a completed observation.
- [ ] Keep ranking state, its recorded observation, and its saved comparison consistent across failures and restarts. Reuse the existing durable saved-data and refresh infrastructure rather than introducing a service or background collector.
- [ ] Handle existing saved rankings without new observation/movement fields gracefully. They remain readable; missing history does not become invented historical observations.
- [ ] Extend the existing Android UI integration tests with controlled sequential HTTP responses to demonstrate gains, losses, unchanged and missing-baseline states, disappearance/reappearance, tied points, search, successful-refresh replacement, failed refresh preservation, and offline restart. Assert product output, not helper behavior or private persistence layout.
- [ ] Reuse or extend the existing interrupted-name-resolution scenario to establish that partial ranking loads cannot advance the successful baseline. Keep the distinct multi-refresh lifecycle checks together where splitting them would lose the interaction guarantee.
- [ ] Check visible arrow colors, sizes, and direction on a supported device/emulator. Do not rely on color alone to communicate movement.

## Blocked by

None (can start immediately).
