## Parent

Part of [spec #16](https://github.com/eshref/TableTennisAz/issues/16).

## What to build

Embed one switchable history chart in the doubles profile. The Points view displays recorded pair rating points; the Rank view displays recorded published positions, with rank #1 at the top. Use the actual device-local observations captured by successful doubles ranking refreshes. Show the first observation as a dated point and explain that future refreshes will add history.

Use the approved Switchable chart layout, prototype variant A. The profile ticket supplies the destination; the ranking-movement ticket supplies the dated observation producer and consistent successful-refresh lifecycle. Keep those shared behaviors rather than adding a second observation collector.

## Acceptance criteria

- [ ] Embed one chart in the pair profile with Azerbaijani Points / Rank controls. Switching changes the displayed measure for the same partnership; it does not navigate to a separate chart screen or add observations.
- [ ] The Points view displays actual recorded published pair points. The Rank view displays actual recorded published positions with rank #1 above worse positions. Labels distinguish points from rank numbers.
- [ ] Both views use the pair's stable identity and actual observation times from successfully saved doubles ranking refreshes. Preserve chronological order, recorded values, and readable date labels, including multiple refreshes on the same date.
- [ ] History is available for pairs whose profiles were not opened during earlier ranking refreshes. Partnerships sharing either player have separate histories.
- [ ] The first recorded observation is a visible single dated point in either view, accompanied by a note that more history will appear after future ranking refreshes. Do not require two observations before displaying the chart.
- [ ] If a compatible saved profile has no recorded history yet, present that state honestly. Do not reconstruct a series from current profile metadata, individual-player history endpoints, match scores, or old event cards.
- [ ] Retain earlier observations as new successful refreshes append data. Display the actual observation span without claiming pre-installation history or continuous collection between refreshes.
- [ ] Opening a cached profile, switching chart mode, or refreshing only medals or match pages creates no ranking observations. Profile points/rank and chart observations remain consistent with the latest saved successful ranking.
- [ ] Saved charts remain available after closing/reopening the app, offline, and after failed ranking refreshes. Follow the existing durable retention and visible-update-time policy.
- [ ] Keep current profile points/rank, gold/silver/bronze counts, both player links, latest tournament/duel matches, pagination, match-detail navigation, and ranking-context restoration intact.
- [ ] Provide accessible descriptions of observed dates and values in both chart modes; keep chart controls readable on supported phone sizes.
- [ ] Extend the existing Android UI integration journeys through real HTTP fixtures, production parsing, controlled time, and durable storage. Demonstrate first-point display, both chart modes, multi-refresh growth, an initially unvisited pair, separation of shared-player partnerships, no extra observations from viewing, failed-refresh preservation, and offline restart.
- [ ] Check actual rendered chart output on a supported device/emulator for single-point visibility, chronological horizontal positioning, rank #1 above worse ranks, readable controls, and fit with medals/recent matches. Use product UI evidence rather than assertions about internal storage.
- [ ] Before adding cases, identify the distinct observable guarantee beyond existing profile, movement, rating-history, freshness, and failure tests. Preserve combined workflow guarantees and avoid tests whose subject is fixture/driver/helper infrastructure.
