# Native Android app for TableTennisAz: first APK

Status: Product scope and testing approach approved. Repository issue-tracker
setup is complete; ready for issue publication. Implementation remains
unauthorized.

## Problem Statement

People who follow table tennis on tabletennis.az want a native Android app for
finding player ratings, viewing player profiles, and reading match results.
They need previously loaded information to remain available without an internet
connection. The website separates amateur and professional rankings and groups
event results separately from duels, while the desired app combines those views.

The project owner does not have the website backend's source code. The app must
work with the website's existing public interfaces, whose available data and
pagination do not establish a complete global recent-results feed.

## Solution

Deliver an installable APK for Android 8.0 and newer, with a native Azerbaijani
interface. Open on a searchable combined individual-player ranking. Provide
player profiles with current rating points, rating history charts, and match
history. Provide a recent-results feed with event matches and duels, result-type
and event filters, match details, and links to player profiles.

Retrieve information directly from the website, using public JSON where
available and parsing HTML where necessary. Show saved data immediately, show
its last update time, and retain it for offline use. Load results in batches
and let the user load more, making partial coverage clear.

## User Stories

1. As an Android user, I want to install the first APK on Android 8.0 or newer, so that I can test the app on my phone.
2. As an Azerbaijani-speaking user, I want the interface in Azerbaijani, so that I can use it in the website's main language.
3. As a mobile user, I want native Android screens and navigation, so that the app works naturally on my phone.
4. As a player, I want the app to open on the combined rankings, so that I can find a player immediately.
5. As a follower, I want amateur and professional players in one list without a category filter, so that I can view them together.
6. As a follower, I want players ordered by rating points from highest to lowest, so that I can compare their published points.
7. As a follower, I want names, points, and combined rank numbers, so that I can understand each player's position in the app's list.
8. As a player, I want equal points to share a rank using 1, 2, 2, 4, so that equal ratings receive equal positions.
9. As a follower, I want the combined rank to remain distinct from the source rank, so that I do not mistake an app-defined position for an official combined ranking.
10. As a player, I want to search the rankings by player name, so that I can find myself or an opponent quickly.
11. As a player, I want a search result to retain the player's combined rank, so that searching does not change the meaning of their position.
12. As a follower, I want to open a player profile from the rankings, so that I can see that player's information and results.
13. As a player, I want current rating points on my profile, so that I can see my published rating.
14. As a player, I want a rating history chart, so that I can see how my points have changed.
15. As a player, I want chart dates and values to reflect the supplied rating history, so that the app does not invent missing years or historical combined ranks.
16. As a follower, I want a player's match history on their profile, so that I can review their recorded results.
17. As a player, I want singles and doubles in match history, so that both formats are represented.
18. As a follower, I want both players on each doubles side to be visible, so that I can identify all participants.
19. As a follower, I want to open match details with the participants, recorded score, and available match date, so that I can understand a result.
20. As a follower, I want to open player profiles from match details, so that I can follow a participant's rating and history.
21. As a follower, I want Results to show a recent-results feed immediately, so that I can browse results without first selecting an event.
22. As a follower, I want event-match and duel results together, so that I can find both in one section.
23. As a follower, I want an All results selection, so that I can browse both result types together.
24. As a follower, I want a Tournament matches selection covering cup and league events, so that I can focus on competition results.
25. As a follower, I want a Duels selection, so that I can focus on matches from the website's duels section.
26. As a follower, I want to select a particular event, so that I can narrow the event-match results to that competition.
27. As a follower, I want loaded results ordered by match date from newest to oldest, so that recent dated results appear first.
28. As a mobile user, I want results loaded in batches, so that opening Results does not require downloading the entire archive.
29. As a follower, I want a Load more action, so that I can retrieve additional results when needed.
30. As a follower, I want partial feed coverage to be clear, so that I do not assume the first batch contains every recent match on the website.
31. As an offline user, I want previously loaded rankings to remain available, so that I can view player ratings without a connection.
32. As an offline user, I want previously loaded player profiles and rating history charts, so that I can review saved player information.
33. As an offline user, I want previously loaded match results and player match histories, so that I can read saved results without a connection.
34. As a user, I want to see when displayed data was last updated, so that I can judge its freshness.
35. As a returning user, I want saved data shown immediately when I open a screen, so that I can start reading while an update is considered.
36. As an online user, I want a screen's data refreshed on opening when it is more than 15 minutes old, so that older saved information is updated.
37. As a mobile user, I want recently saved data reused when I open a screen, so that each visit does not cause another download within 15 minutes.
38. As an online user, I want manual refresh at any time, so that I can request an update before the automatic age limit.
39. As an offline user, I want saved information and its update time to remain visible, so that losing connectivity does not erase what I can read.
40. As a returning user, I want downloaded information to survive closing and reopening the app, so that offline access does not depend on the current session.
41. As an offline user, I want saved information retained until I clear the app's stored data or uninstall it, so that age alone does not remove it.
42. As a user, I want a successful refresh to update the saved information and its update time, so that future offline visits show that retrieved version.
43. As a user, I want a failed refresh to preserve the last successfully retrieved information, so that a website or connection failure does not remove usable saved data.

## Implementation Decisions

- **Current state:** The repository contains project setup, planning documents,
  a domain glossary, ADRs, and a throwaway comparison prototype. It has no
  production Android application or production test suite. This delivery needs
  a new Android application rather than changes to an existing application.
- **Platform and delivery:** Use native Android screens, support Android 8.0
  (API level 26) and newer, and deliver an APK for real-device testing. The
  interface is Azerbaijani only for this delivery. Keep text localizable for
  possible later languages. A specific UI toolkit, dependency set, and build
  tool versions have not been selected in the approved plan.
- **Logical responsibilities:** Provide rankings, player-profile and rating
  history, results-feed and match-detail experiences, supported by website
  access/parsing and durable local storage. These are responsibilities, not a
  requirement to create a separate build module for each one.
- **Website access:** Fetch directly from tabletennis.az. Use public JSON
  interfaces when available and parse server-rendered HTML for other required
  information. Do not introduce a hosted adapter or require backend source
  changes. There is no approved new server-side API contract or backend schema
  migration.
- **Individual rankings:** Retrieve both source categories and combine players
  into one list. The default source ratings page contains amateurs only; the
  professional category must also be requested. Preserve source player
  identities and numeric points. Do not duplicate a player merely because the
  same identity appears in more than one retrieved response.
- **Combined ranks:** Sort by points descending. Equal points share a rank and
  occupy the corresponding positions before the next rank: 1, 2, 2, 4. Compute
  ranks for the combined list, not for a filtered search result. These are
  app-defined combined ranks; the source ranks remain category-specific. The
  website has not established an official combined ranking or cross-category
  point comparability in the inspected published material.
- **Profiles and charts:** Show current points, a rating-points history chart,
  and match history. Points history is distinct from rank history. The website's
  historical source positions do not establish historical combined ranks.
  Preserve available chart dates without inventing missing years. Do not assume
  the final source position-history point equals a current source rank.
- **Match representation:** Support singles and doubles independently of whether
  the result is an event match or a duel. A doubles side contains two players;
  retain links for every participant. A match detail shows participants, recorded
  score, and available date information. Do not infer an exact match time from a
  source row that provides only a date.
- **Navigation:** Rankings is the launch screen. Searching by name leads to a
  player profile. Results opens the Recent results design selected after
  comparing the prototype with Event-first. Opening a result leads to match
  details, from which player profiles are accessible. The comparison's sample
  records establish the navigation choice, not production data completeness.
- **Results filters:** Include All results, Tournament matches, and Duels
  selections. Tournament matches includes cup and league events. Retain the
  event filter for event-match results. The amateur/professional filter is
  absent from rankings; result-type filters are a separate decision.
- **Results aggregation:** The source event-results chooser and duels list are
  separate. Combine retrieved results by source match identity, handle source
  pagination, and order loaded results by match date descending. Source match
  IDs and row positions do not establish exact within-day recency.
- **Partial coverage and pagination:** Load results in batches and provide Load
  more. The first batch may omit recent matches elsewhere in the source archive.
  Do not describe partial aggregation as a complete global recent feed. A
  selected event's results must not be confused with an empty result simply
  because that event has not yet been included in the general feed's loaded
  batch. Batch sizes and source scheduling require implementation tuning; the
  approved scope does not prescribe numerical limits.
- **Saved data:** Use durable on-device storage for retrieved rankings, profiles,
  charts, match histories, and results. Store the last successful update time
  with the information it describes. Previously loaded information must be
  usable across app sessions without a connection.
- **Refresh policy:** On screen opening, show saved information immediately.
  When online, refresh it if it is more than 15 minutes old. A manual refresh is
  available at any time when online. With no saved information, an online first
  load retrieves it. The 15-minute rule is checked on screen opening, not an
  instruction to schedule periodic background downloads.
- **Retention and refresh failure:** Successful retrieval updates saved data and
  its update time. Failed retrieval must not masquerade as a successful update
  or discard usable saved information. Age alone never deletes downloaded data;
  retain it until the app's stored data is cleared or the app is uninstalled.
  A first offline visit to information that was never downloaded cannot provide
  that information and must not fabricate a result or update time.

## Testing Decisions

- **Approval:** The project owner confirmed this testing approach for the
  specification. This approval does not authorize application coding.
- **Primary seam:** Drive the native app through its user interface
  while an HTTP fixture server supplies representative website responses at the
  website boundary. Use the production HTTP client, HTML/JSON parsing, ranking
  calculations, local persistence, and refresh behavior. This is one primary
  application-level acceptance seam; avoid a separate test seam for every
  screen or parser helper.
- **Existing seams and prior art:** There are no existing Android application
  tests or test framework to extend. The interactive prototype and its temporary
  navigation checks are design evidence, not production integration coverage.
  Select a suitable Android UI/integration runner during implementation instead
  of assuming a framework is already present.
- **What makes a good test:** Assert user-visible names, points, ranks, chart
  values and labels, result contents, navigation, update times, and offline
  behavior. Website requests captured at the external HTTP fixture server may
  verify interaction with that boundary. Do not assert internal database tables,
  private methods, parser structure, fixture helpers, or test-driver behavior.
- **Coverage discipline:** Inspect the implemented behavior and existing tests
  before adding cases. Each case must add a distinct observable guarantee or
  regression. Prefer data-driven cases for the same behavior, keep Arrange,
  Act, and Assert phases clear, and retain combined navigation or persistence
  workflows when they cover interactions absent from isolated scenarios. Do
  not duplicate full integration coverage with implementation-mirroring unit
  tests.
- **Rankings workflow:** Through the app, exercise both source categories,
  combination and descending points, equal-point ranks, and player-name search
  that preserves combined rank. Continue from a search result through a player
  profile and a linked match to verify that identities and displayed information
  stay consistent across the journey.
- **Profile and chart coverage:** Verify that current points and rating history
  come from their intended source data, dates retain the supplied information,
  and no historical combined rank or missing year is invented. Check the chart
  through its values and accessible labels rather than brittle pixel images.
  Include offline reuse of previously loaded profile and chart information.
- **Results coverage:** Supply event and duel responses, singles and doubles,
  and an event spanning multiple source pages. Verify visible result-type and
  event filtering, date ordering, Load more, and all four linked players in
  doubles. Include overlap between retrieved pages or sources to verify that
  one source match is not shown twice, and verify the stated partial-coverage
  presentation.
- **Freshness and persistence coverage:** Control response timing and the test
  clock through the test environment. Verify saved data is visible before a
  delayed refresh completes; cover ages just below, exactly at, and just above
  15 minutes; verify manual refresh before the age limit and online first load.
  Relaunch the app offline to verify durable storage through its screens, and
  advance age beyond the freshness limit to verify retention without deletion.
- **Failures:** Exercise unavailable connectivity, failed HTTP responses, and
  unusable changed markup through the website boundary. Check that previously
  retrieved data remains readable and that its last successful update time is
  preserved. Verify an honest empty/error state when no downloaded data exists.
- **Live compatibility check:** In addition to deterministic fixture-backed
  acceptance tests, perform a small read-only integration check against the
  actual website using the production Android client configuration. Verify
  transport and response compatibility for the required source types without
  depending on fixed live player counts, rankings, or scores. The fixture
  server alone cannot establish compatibility with live-site HTTP behavior.
- **APK verification:** Once implementation is explicitly authorized, install
  and launch the APK on a supported device or emulator. Cover the minimum
  supported Android version and a current version where available, then provide
  the APK for the project owner's phone testing. No tests have been implemented
  or run as part of this specification request.

## Out of Scope

- Starting production implementation or APK development under this spec-writing
  request; the project owner approved planning only.
- Google Play publication or other public-store release in the first delivery.
- iOS, a replacement website, or a WebView-based replacement for the chosen
  native experiences.
- Additional interface languages in the first delivery.
- Separate doubles-pair rankings or an amateur/professional ranking filter.
- Backend source changes, a new hosted adapter service, or a new backend API.
- Dedicated tournament browsing beyond the event selection needed to filter
  match results, and table reservations or booking workflows.
- Additional profile features beyond the approved current points, rating history
  chart, and match history.
- New account, payment, shop, tournament-entry, live-scoring, or notification
  workflows.
- Claiming official status for the app's combined ranks, inventing historical
  combined ranks, or guaranteeing that a partial feed contains every recent
  website match.
- Periodic background downloads or age-based deletion of saved data.

## Further Notes

- **Authorization:** The project owner explicitly approved the plan only. This
  specification request authorizes synthesis and publication, not application
  coding. A future ready-for-agent label describes specification readiness and
  does not replace the required explicit instruction to begin implementation.
- **Publication status:** GitHub Issues is configured for eshref/TableTennisAz,
  and the five default triage labels are available. The specification has not
  yet been published. Use the configured tracker and ready-for-agent triage
  role for publication. The testing boundary has been confirmed as required
  by to-spec.
- **Implementation tickets:** The approved nine-ticket breakdown derived from
  this local specification has been published to GitHub with ready-for-agent
  labels and native blocking relationships. See the
  [published ticket index](../tickets/android-first-delivery.md). Application
  implementation still awaits explicit authorization.
- **Decision sources:** Use the approved discovery plan, domain glossary, and
  ADRs for native screens, direct website access, combined ranking, and recent
  results. Preserve the comparison prototype on the local
  `prototype/results-navigation` branch; Recent results won the comparison.
  The prototype uses illustrative records and is not production Android code.
- **Website findings:** Essential rankings, profile information, and match
  lists are server-rendered HTML. Public JSON was verified for player points
  history and source position history. The inspected scripts did not reveal a
  complete ratings/profile/results API; this does not prove another API does
  not exist.
- **Data limitations:** The event-results chooser lists completed competitions
  without dates, and its ordering is undocumented. A single event can span
  multiple result pages. Duels have separate pagination. The shared latest-results
  sidebar was older than verified event results, so it cannot establish complete
  recent coverage. These findings explain the agreed partial-feed policy.
- **HTTP compatibility:** Public research requests succeeded with curl while
  requests from another default HTTP client returned 403. Verify actual Android
  client behavior rather than assuming all clients have equivalent access.
- **Upstream changes:** Website markup changes can require parsing fixes and a
  new APK. This is an accepted consequence of direct access without a hosted
  adapter.
- **External uncertainty:** Permission to reuse the website's data and interfaces
  has not been established in the conversation. Publishing this specification
  does not establish website ownership or an official affiliation.
