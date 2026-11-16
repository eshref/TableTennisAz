# Android app discovery

## Approval status

The project owner approved this plan only. Implementation and APK development
are not authorized by this approval. Wait for an explicit instruction to begin
coding.

The approved plan has been synthesized into the
[first-delivery specification](specs/android-first-delivery.md). Its testing
approach is approved. GitHub Issues and the triage-label mapping are configured;
the specification is ready for publication.

The approved nine-ticket breakdown has been
[published to GitHub](tickets/android-first-delivery.md), with ready-for-agent
labels and native blocking relationships. Ticket publication does not authorize
application implementation.

## Confirmed direction and constraints

- Build an Android application for https://tabletennis.az/.
- The project owner does not have the website backend's source code.
- Resolve product and architecture decisions one question at a time before implementation.

## Confirmed first-release scope

- Browse individual-player ratings. Separate doubles-pair rankings are deferred.
- View player profiles.
- Browse match results.

Match results and player histories include both singles and doubles. Doubles
results identify both players on each side.

The rankings screen supports searching by player name. Selecting a matching
player opens their profile, providing access to their rating and match history.
Show amateur and professional players together in one list, without a category
filter. Order the merged list by points, highest first, and show player names,
points, and rank numbers. These combined ranks are defined by the app and differ
from the website's category-specific ranks. Players with equal points share a
rank, with the next rank skipping the occupied positions: for example, 1, 2, 2,
4. See [the combined-ranking decision](adr/0003-combined-player-ranking.md).

The first release focuses on these journeys. Dedicated tournament browsing and
table reservations are outside the agreed initial focus.

## Confirmed player-profile content

Profiles include current rating points, match history, and a rating-points
history chart. The chart shows the website's published points history; it does
not establish a history of the app's combined ranks.

## Confirmed app experience

Use native Android screens and navigation for the agreed features. See
[the decision](adr/0001-native-android-screens.md).
The app opens on the combined individual-player rankings list, with player-name
search available immediately.

The Results section opens a recent-results feed containing both event matches
and duels. Provide a selection filter for All results, Tournament matches, or
Duels. Tournament matches includes cup and league events, with the previously
agreed event filter available for those matches. Users can open a match result
and then its players' profiles.
This resolves Q14 in favor of the Recent results prototype. See
[the navigation decision](adr/0004-recent-results-navigation.md).

Load results in batches, with a Load more button to retrieve additional results.
The first batch is partial and may not contain every recent match on the website.
Sort retrieved results by match date without claiming archive-wide completeness.
Batch sizes and source-page scheduling remain implementation details to resolve.

## Confirmed language

The first release uses Azerbaijani only. Keep app text in localizable resources
so additional interface languages can be added later.

## Confirmed offline behavior

Allow users to view previously loaded ratings, player profiles, and match results
without an internet connection. Show when the displayed data was last updated
so cached information is distinguishable from freshly retrieved information.

When a screen opens, show saved data immediately. If online and the saved data
is more than 15 minutes old, refresh it from the website. Manual refresh is
available at any time when online. Without saved data, an online first load
fetches the data. Offline screens retain the saved data and its update time.
This is a check on screen opening, not a periodic background-download schedule.
Retain downloaded ratings, profiles, charts, and results until the app's stored
data is cleared or the app is uninstalled. Successful refreshes update saved
data; age alone does not delete it.

## Confirmed first delivery

Support Android 8.0 (API level 26) and newer.

Deliver an installable APK for the project owner to test on a real Android
phone. Consider Google Play publication afterward; store publication is not
part of this first delivery.

## Confirmed data integration

The Android app fetches the website directly, using its public JSON endpoints
where available and parsing HTML for the remaining data. No separately hosted
adapter is planned for the first release. Website markup changes may require
an app update. See [the decision](adr/0002-direct-website-data-access.md).

## Website observations

Read-only inspection on 2026-11-15 found public Azerbaijani pages for
[player ratings](https://tabletennis.az/players),
[player profiles](https://tabletennis.az/players/1048),
[tournaments](https://tabletennis.az/events/938),
[match details](https://tabletennis.az/matches/8659), and
[table reservations](https://tabletennis.az/reservation).
No forms were submitted.

Two public player-chart endpoints returned JSON:
`/api/getPlayerPoints?id=1048` and `/api/getPlayerPositions?id=1048`.
This does not establish a complete or supported API for an Android app.
Permission to reuse the site's data and APIs remains unconfirmed.
Mobile rendering and workflows requiring authentication have not been verified.

Further read-only inspection established that:

- Ratings, profile details, and match lists are present in server-rendered HTML.
  The inspected scripts did not reveal a complete JSON API for these screens.
- [Results](https://tabletennis.az/matches/results) starts with an event chooser.
  [Event results](https://tabletennis.az/matches/results/event/938) are paginated;
  [player results](https://tabletennis.az/matches/results/player/1048) provide
  another route into match history.
- Both [singles](https://tabletennis.az/matches/30799) and
  [doubles](https://tabletennis.az/matches/8659) results exist. A match side can
  contain one player or two players.
- Sampled ratings and event-chooser HTML responses were approximately 1.46 MB
  and 1.36 MB uncompressed, respectively. Loading and caching must account for
  large source pages.
- Search behavior is unverified. Current profile rank must not be assumed to
  equal the final rank-history chart value; the sampled values differed.
- Sampled chart dates contain day and month without a year. Chart presentation
  must preserve the available date information without inventing missing years.
- The default ratings page matches `?level=amateur`. A separate
  [`?level=pro`](https://tabletennis.az/players?level=pro) response contains the
  professional players. Both groups restart positions at 1. The sampled lists
  had 818 amateur and 3 professional players, with no overlapping player IDs.
  [Published rating rules](https://www.tabletennis.az/posts/2) do not explicitly
  establish cross-category point comparability or an official combined ranking.
- [Duels](https://tabletennis.az/matches/duellos) has its own paginated results
  list. Loading only event results would omit these matches.
- The event-results chooser shows completed competitions but no dates. Its
  overall ordering is undocumented. Match-list rows provide dates but not times;
  neither match IDs nor row order establish exact within-day recency. The shared
  latest-results sidebar was older than the sampled event results and cannot
  establish a complete recent feed.
- Every selected event's result pages must be considered for complete coverage
  of that event. Event 938 had 40 matches on page 1 and another 27 on page 2.
  Loading a batch of listed events cannot establish globally latest results.
- Public GET requests succeeded with curl, while the research client's default
  Python HTTP requests received 403. Android HTTP compatibility needs verification.

## Scope review

Product decisions through Q21 are recorded above. The project owner approved
the first-delivery scope at Q22, explicitly limiting approval to the plan.
Additional profile features and public store distribution are outside this
agreed first delivery unless explicitly added later.

## Doubles-ranking follow-up discovery

This follow-up interview on 2026-11-16 explored the previously deferred
doubles-pair ranking. The project owner confirmed shared understanding and
approved the plan at Q14. Discovery is complete; this approval does not
authorize tracker publication. Application implementation was subsequently
explicitly requested with the implement skill, on a new task branch.

Confirmed decisions:

- Rank doubles pairs separately. A player can appear in multiple partnerships.
- Use the website's published pair positions and points, including distinct
  positions for pairs with equal points. The individual-player combined-rank
  rule remains unchanged.
- Add a Players / Doubles switch to Rankings, with Players selected when the
  app opens. Interface wording remains subject to the agreed Azerbaijani language.
- Each doubles row shows position, both players' full names, and pair points. Bonus
  and medal counts are outside this feature's first version.
- Selecting a pair opens its match results, with links to both player profiles.
- Pair history includes tournament matches and doubles duels played by those
  two players together as partners, using the website's pair identity.
- Pair results start with the newest matches and offer Load more for older
  results.
- Returning from pair results or a player profile restores the Doubles
  selection, search text, and ranking scroll position. A fresh app launch still
  opens on Players.
- Name search matches either player's full name, including partial-name
  searches, shows all matching partnerships, and retains published positions.
- Resolve and save full names for all pairs on first load, displaying progress
  until the ranking and name search are complete. Reuse saved identities on
  later visits. Do not present a search over unresolved names as complete.
- Name search is the only doubles-ranking filter in this version; event
  filtering is deferred.
- Doubles rankings and pair results follow the existing saved-data policy:
  display saved information immediately with its last successful update time;
  refresh on opening when online and over 15 minutes old; allow manual refresh
  online. Retain saved information offline and on refresh failure. The existing
  first-load and retention rules also apply.

Read-only website inspection on 2026-11-16 established that
[doubles rankings](https://tabletennis.az/players/duos) publishes pair identities,
abbreviated names, positions, and points. Equal points can have separate source
positions. The inspected response contained 848 pairs in approximately 4.17 MB
of HTML, without ranking pagination. The site offers player and event filters;
the app's first doubles-ranking version uses name search only.

The public `/api/getDuoInfo?id=<pairId>` endpoint supplies both players' identities,
full names, profile links, and a pair-results link. The inspected
[pair-results page](https://tabletennis.az/matches/results/duo/584) is paginated.
The ranking page includes a directory of full player names and IDs but does not
map pair IDs to their members. Its abbreviated pair names are ambiguous, so
matching abbreviations to the directory cannot reliably establish membership.
No bulk membership interface has been identified; the known route needs a
metadata lookup per pair. This can make first load slow, a trade-off accepted
to preserve full names and accurate search.

Pair-history inspection confirmed both event matches and doubles duels: pair
151's page 6 includes duel 20006 as well as tournament matches. Pair 584's
six-page history had 275 matches with dates descending across pages. Rows supply
dates without times, so exact within-day order is not established. Respect
pagination's disabled controls; an out-of-range page can return HTTP 200 with
an empty list. These observations do not establish archive-wide completeness.

Decisions through Q13 are recorded above and were confirmed together at Q14.
The subsequent implement request authorizes application coding and a local
commit on the requested task branch. Tracker publication has not been requested.
Implementation and verification are recorded in
[the doubles verification notes](android-verification.md#doubles-ranking--version-030).

## Remaining engineering work and external uncertainties

- Tune feed batch sizes and source-page scheduling during implementation,
  preserving the agreed partial-coverage behavior and Load more action.
- Verify website access from the Android HTTP client and the reliability of
  parsing, pagination, chart dates, and offline refresh behavior.
- A complete recent-match API and an official combined ranking have not been
  established. Preserve the distinctions documented above.
- Permission to reuse the website's data and interfaces remains unconfirmed.
- Website changes can require parser changes and another APK delivery, as
  documented in the direct-access decision.

## Results navigation prototype outcome

The [throwaway prototype](prototypes/results-navigation-prototype.html) compares
event selection first with a recent-results feed filtered by event. The project
owner selected **Recent results** for Q14. It uses illustrative data, not live
website results. Both options allow opening match details and player profiles; the
rankings section demonstrates name search and the agreed shared-rank rule.
The prototype begins in Results to make comparison immediate; the agreed app
launch screen remains Rankings.

The production feed must combine event-result and duel pages and handle their
pagination. The agreed batching policy allows partial coverage; implementation
must preserve this limitation and the visible last-update time.

Preserve the comparison on the local `prototype/results-navigation` branch as a
primary source for the decision. Prototype code is not production Android code.

Create glossary entries when domain terminology is settled. Record architectural
decisions when a meaningful trade-off has been resolved.
