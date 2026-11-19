# Doubles profiles, observed histories, and ranking movement

This specification's original refresh interval has been superseded by the
[48-hour freshness policy](../../README.md#freshness-and-offline-use).

## Problem Statement

People following a doubles pair can currently open its match results from Doubles rankings, but cannot see a complete doubles profile with current points, rank, medals, and historical performance. The website itself does not provide a doubles profile equivalent to an individual player's profile, and no usable public source of historical pair points or ranking positions has been established.

Users also cannot see how many places each pair has gained or lost after refreshing the doubles ranking. They need both rating history and ranking history, with an honest indication of the observations the app actually holds.

## Solution

Expand the existing pair-results destination into a native doubles profile. Selecting a pair in Doubles rankings opens its full names, links to both individual player profiles, current published pair points and position, gold/silver/bronze medal counts, latest matches, and an embedded history chart with a Points / Rank switch. Place rank #1 at the top of the ranking chart.

Build both histories from doubles ranking observations saved on the device whenever doubles rankings successfully refresh. Rating history shows the first observed points and each subsequent points change. Ranking history follows published position independently. Dates record when the device saw the first value or observed a change. A single entry includes a note explaining that another entry appears when the selected value changes.

In Doubles rankings, compare each pair's new published position against the previous successful refresh saved on the same device. Show a small green up arrow and the number of places gained, or a small red down arrow and the number of places lost. Show nothing when the position is unchanged or no previous position is available.

Carry forward existing Azerbaijani interface wording, individual-player links, pair match pagination, navigation restoration, saved-data retention, offline access, and refresh policy.

## User Stories

1. As a follower, I want selecting a pair in Doubles rankings to open that pair's profile, so that I can review its performance in one place.
2. As a follower, I want both partners' full names on the doubles profile, so that I can identify the partnership unambiguously.
3. As a follower, I want links to both individual player profiles, so that I can explore either partner's playing record.
4. As a follower, I want the pair's current published rating points on its profile, so that I can see its latest saved rating.
5. As a follower, I want the pair's current published ranking position on its profile, so that I can see its standing among doubles pairs.
6. As a follower, I want separate gold, silver, and bronze counts for the pair, so that I can see the partnership's published medal record.
7. As a follower, I want a published zero medal count to be displayed as zero and missing medal information to remain distinguishable, so that unavailable information is not presented as a known result.
8. As a follower, I want the pair's latest matches on its profile, so that I can review its recent results.
9. As a follower, I want pair history to include tournament matches and doubles duels, so that both kinds of results remain represented.
10. As a follower, I want pair history limited to those two players competing together, so that another partnership involving either player does not contaminate the record.
11. As a follower, I want match results ordered newest first, so that I can find recent matches quickly.
12. As a follower, I want Load more for older pair results, so that opening the profile does not require loading the entire archive.
13. As a follower, I want to open match details from the pair profile, so that I can inspect a recorded result.
14. As a follower, I want returning from profiles or match details to preserve the Doubles selection, search text, and ranking scroll position, so that I can continue browsing where I left off.
15. As a follower, I want an embedded chart on the pair profile, so that I can inspect historical performance alongside its other information.
16. As a follower, I want a Points / Rank switch for that chart, so that I can choose between rating history and ranking history.
17. As a follower, I want the Points view to show observed published pair points over time, so that I can see how the pair's rating has changed.
18. As a follower, I want the Rank view to show observed published pair positions over time, so that I can see how its standing has changed.
19. As a follower, I want rank #1 at the top of the ranking chart, so that an improvement in standing moves upward visually.
20. As a follower, I want observations associated with their actual recording dates, so that I know when the displayed values were observed.
21. As a follower, I want the first observation displayed as a single dated point, so that history is useful before a second refresh.
22. As a follower, I want a note explaining that future changes add entries in the selected mode, so that I understand why I see a single point.
23. As a follower, I want both histories collected whenever doubles rankings successfully refresh, so that recording does not depend on whether I previously opened a particular pair's profile.
24. As a follower, I want history to start with the device's first recorded observation and reflect gaps between refreshes, so that I do not mistake it for a complete historical archive.
25. As a follower, I want histories kept separate for different partnerships involving the same player, so that each pair's performance remains distinct.
26. As a returning user, I want earlier recorded observations preserved as new observations arrive, so that the chart grows instead of replacing its past.
27. As a returning user, I want saved histories to survive closing and reopening the app, so that a restart does not erase recorded performance.
28. As an offline user, I want previously saved pair profiles and histories available, so that I can review them without connectivity.
29. As a follower, I want a small green upward arrow with the number of places gained when a pair improves, so that I can quickly spot an upward movement.
30. As a follower, I want a small red downward arrow with the number of places lost when a pair falls, so that I can quickly spot a downward movement.
31. As a follower, I want movement compared against the previous successful ranking refresh saved on the same device, so that every displayed change has a consistent baseline.
32. As a follower, I want no movement indicator when a pair keeps the same position, so that unchanged rows remain uncluttered.
33. As a follower, I want no movement indicator when the previous saved ranking lacks the pair, so that an unknown change is not shown as a measured gain or loss.
34. As a first-time user, I want no movement indicators until a comparison is available, so that the app does not invent changes on the first download.
35. As a follower, I want movement to use the website's published pair positions even when points tie, so that the app preserves the doubles ranking's meaning.
36. As a follower, I want name searches to retain the pair's published position and movement, so that filtering does not recalculate either value.
37. As a returning user, I want the latest saved movement retained when I reopen rankings without refreshing, so that reopening a screen does not create a new comparison.
38. As a user, I want failed or incomplete ranking refreshes to preserve the last successful ranking, histories, movement, and update time, so that a partial download does not become recorded performance.
39. As a returning user, I want saved content shown immediately with its successful update time, so that I can read while an update is considered and judge its freshness.
40. As an online user, I want the existing refresh-on-open policy for data older than 15 minutes and manual refresh, so that the new profile fits the app's current behavior.
41. As a user, I want cached screen openings and chart switches to reuse saved observations, so that viewing information does not manufacture new history.
42. As an Azerbaijani-speaking user, I want profile headings, chart controls, medals, movement descriptions, and status messages in Azerbaijani, so that the new feature matches the app.
43. As a user of assistive technology, I want chart observations and ranking movement described with their meaning and values, so that I can understand performance without relying on line shapes or arrow colors.
44. As an existing user, I want previously saved doubles rankings, identities, and match results to remain usable after the update, so that adding profiles does not discard my saved information.

## Implementation Decisions

- **Build on existing behavior:** Expand the pair-results screen into the doubles profile and retain the established ranking-to-pair navigation. Reuse existing pair identity, results pagination, player-profile links, match-detail navigation, and saved-screen infrastructure. This is an Android application feature; it does not add or modify the website backend.
- **Pair identity:** Key profiles, observations, medals, and movement by the website's stable pair identity. A player can belong to multiple pairs. Different partnerships remain separate even when they share a player.
- **Published values:** Reuse the doubles ranking integration for current points and positions. Preserve the website's positions, including distinct positions for equal points. Do not apply the amateur singles competition-ranking rule to doubles.
- **Pair medals:** Retrieve pair-specific gold/silver/bronze counts from the public pair metadata interface. These are the partnership's published counts, not the sum of the two individual players' medals. Validate returned identity before assigning data to a pair. Refresh mutable medal information under the existing freshness policy rather than treating the identity cache as permanently current medal data. Preserve saved values on refresh failure; do not invent zero counts for absent data.
- **History source:** Follow the accepted direct-website-access and on-device-observation decisions. No historical doubles feed has been verified. Individual-player history endpoints accept player identities and cannot supply pair histories, even when a numeric ID happens to match. Match scores and old event cards do not establish historical pair points or positions.
- **Observation capture:** Each successful doubles ranking refresh records the published points and positions of its pairs, including pairs whose profiles have not been opened. Save pair identity, observation time, points, and published position. Use the app's existing clock boundary, retaining enough date/time information to order multiple refreshes and label actual observation dates without inventing match-time values.
- **Successful refresh boundary:** A ranking observation represents a complete, successfully validated and saved ranking refresh, not a downloaded fragment or an intermediate identity-resolution step. Keep current ranking state, its observation, and its movement comparison consistent. Network, parsing, validation, or persistence failure must not advance the successful baseline or leave partial history visible.
- **History retention:** Append new successful observations without overwriting prior observations. Merely reading a cache, recreating a screen, refreshing only match pages or medals, or switching chart mode must not add a ranking observation. Histories remain device-local and follow existing durable retention: no age-based deletion; clearing app data or uninstalling removes them. There is no account or cross-device synchronization.
- **History presentation:** Use one embedded chart with Xal and Mövqe controls. Read all saved observations for the selected pair chronologically, keeping the first value and each change in the selected measure. An observation without the pair supplies no value. A value that returns after an intervening change gets a new dated entry. The chart dots, date labels, selectable rows and accessibility descriptions use the same series. Use elapsed time for horizontal spacing and place position #1 at the top. Show the latest entry by default. When switching modes from an inspected row, show the latest entry in the new mode at or before the inspected time. Render an explicit empty state with no observations and a dated point with an explanatory note when the selected mode has one entry.
- **Movement calculation:** Compare the current published position with the same pair's position in the immediately preceding successful ranking refresh on that device. The size of the movement is the absolute difference between the two positions. Moving #12 to #9 shows a green upward arrow and 3; moving #9 to #11 shows a red downward arrow and 2. Show no arrow, count, dash, or zero when unchanged or when the pair is absent from the previous ranking. Do not fall back to an older position when the immediately previous ranking lacks the pair.
- **Movement lifecycle:** Keep the latest computed movement with saved ranking state so reopening or going offline preserves it. A later successful refresh replaces the comparison; if that refresh has the same positions, the indicator disappears. Failed refreshes retain the last successful comparison. Searching changes only which rows are displayed, never the baseline or published positions.
- **Persistence compatibility:** Extend the existing durable data models and stores for profile medals, dated pair observations, and movement state. Handle older cached rankings, pair identities, and pair results without these additions gracefully. Do not manufacture historical observations during migration; keep previously readable saved data available.
- **Profile freshness:** Carry forward immediate saved-data display, visible successful update times, refresh on opening online when data is over 15 minutes old, manual refresh online, saved-data retention after failure, and offline access. Displayed points, rank, and histories must agree with the latest saved successful ranking observation rather than claiming to be live measurements.
- **Match history and navigation:** Retain tournament matches and doubles duels for the specific pair, newest-first ordering, Load more, overlapping-result deduplication, correct player links and match details, and restoration of Doubles selection/search/scroll when returning. Chart additions do not change result coverage or website pagination semantics.
- **Product language:** Keep the native Azerbaijani interface, current Android support, and existing singles behavior. Points / Rank are the English design labels; shipping labels and accessibility descriptions must use the app's language.

## Testing Decisions

- **Approved test boundary:** The project owner approved extending the existing Android UI integration tests with a real local HTTP fixture server and durable storage, supplemented by device checks for chart layout and arrow colors. Prefer this established boundary over introducing a new testing interface.
- **Exercise production paths:** Drive ranking selection, refresh, pair-profile navigation, chart-mode switching, paging, restart, and offline use through the actual Android screens. Use the production HTTP client, HTML/JSON parsing, saved-data behavior, and controlled app clock behind those screens. Tests must assert observable product behavior rather than private methods, serialization layout, or fixture/helper implementation.
- **Prior art:** Existing doubles workflow tests already cover pair identities, source positions despite tied points, either-partner search, tournament/duel pagination, individual profile links, navigation restoration, and offline relaunch. Existing pair-history refresh tests establish preservation after later-page failures and rejection of another partnership. Existing rating-history tests establish visible source values, accessible chart descriptions, single-source identity, and offline persistence. Existing doubles freshness/failure suites cover the saved-screen policy. Extend these scenarios where their workflows naturally prove the new guarantees.
- **Distinct integration guarantees:** Before adding a test or data row, inspect existing coverage across test types. Add scenarios for previously uncovered pair profile points/rank/medals, pair-specific medal refresh, both chart views, first-point presentation, multiple dated observations, and separation of partnerships sharing a player. Use a refresh-to-profile journey to prove that unvisited pairs also accumulate history.
- **Movement scenarios:** Cover upward movement, downward movement, unchanged position, first observation, and a pair missing from the immediately previous ranking. Include a sequence where a pair disappears and returns to establish that older observations do not substitute for the required baseline. Assert both visible counts and directional/color semantics. Reuse parameterized scenario data where supported by the repository's existing runner, rather than copying the same behavioral test for different values.
- **Refresh lifecycle:** Use sequential controlled HTTP responses to prove that a successful refresh records history and computes movement together, a cached reopen does neither, a later unchanged success clears movement, and failures during download or validation preserve the successful ranking, observation history, movement, and update time. Exercise interrupted name resolution through the existing integration scenario; it must not create partial observations.
- **Durability and compatibility:** Restart production screens with saved storage intact, then disable the HTTP source and verify histories, medals, movement, and update times through visible outputs. Prepare pre-feature saved data to verify it remains usable without invented historical observations. Preserve combined workflows that establish lifecycle guarantees absent from isolated cases.
- **Chart and UI checks:** Assert observed points, positions, dates, selected mode, and accessible descriptions through product UI. Supplement with device/emulator checks of the embedded layout, single-point visibility, chronological horizontal positioning, rank #1 above worse ranks, readable controls, and green/red movement arrows at supported phone sizes. These checks use rendered product output, not database or other internal-state evidence.
- **Live compatibility:** Adapt the existing opt-in live doubles screen check to open a real pair profile and confirm the public ranking/metadata/results integrations remain compatible. Do not assert fixed live points, positions, medals, or movement values. Deterministic fixture integration tests establish the multi-refresh behavior.
- **Test discipline:** Keep Arrange, Act, and Assert phases clear using the repository's exact comments. Add no tests whose subject is the test fixture, driver, or helper. Additional isolated unit tests are unnecessary when the product integration scenarios already establish the same guarantees. This specification change itself does not add or run application tests.

## Out of Scope

- A doubles-profile feature or historical API on the website backend.
- A hosted adapter, shared history database, account system, background collection service, or cross-device synchronization.
- Recovering history before the device's first recorded observation, reconstructing pair points from match scores, or substituting individual-player history for pair history.
- Recalculating website doubles positions or importing the amateur singles tie-ranking policy.
- Adding ranking-position charts, medal features, or movement indicators to individual-player profiles or singles rankings.
- Two permanently stacked charts, a dedicated pair-history chart screen, or a combined chart with two measurement axes.
- Bonus points, additional doubles-ranking filters, changes to event/result coverage, or a new match-pagination model.
- Rewriting or redesigning the entire app, changing Android support, or adding public store distribution.
- Production implementation, deployment, or pushing Git branches as part of this spec-publication task.

## Further Notes

- The project owner explicitly approved the complete plan after Q9, then requested specification publication with the to-spec skill. The proposed existing test boundaries were separately confirmed during specification preparation.
- The single-point startup decision is Q8. The no-indicator rule in Q6 supersedes the earlier Q5 gray-dash proposal: unchanged and missing-baseline rows both show nothing.
- The selected layout came from the Switchable chart prototype, variant A. The comparison used illustrative sample records.
- Read-only inspection on 2026-11-16 established current pair points and positions in the doubles ranking, and membership, pair medals, and a results link in the pair metadata response. No usable historical pair series was identified in the inspected public interfaces; that bounded finding is not proof that no other interface exists.
- The history source follows the accepted decision “Build doubles histories from on-device ranking observations” and retains the existing “Fetch website data directly from Android” architecture.
