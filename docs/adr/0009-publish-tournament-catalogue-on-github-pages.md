# Publish a generated tournament catalogue on GitHub Pages

The public Results index lists finished tournaments, and their dates and formats
require additional source pages. Generate and publish a compact tournament
catalogue on the project's GitHub Pages site on a schedule, so each Android
installation can fetch and cache one catalogue for both searchable tournament
lists and their new-tournament indicators.

This extends the [first-release data access decision](0002-direct-website-data-access.md).
A scheduled job centralizes the source requests and parsing needed to build the
catalogue. Android fetches the selected tournament's match results and the duel
feed directly from tabletennis.az.

The [Pages workflow](../../.github/workflows/tournament-catalogue.yml) builds
the existing site from `docs/`, adds `tournaments-v1.json`, and deploys the
whole site. After merging the workflow, set the repository's **Settings →
Pages → Source** to **GitHub Actions** and dispatch **Tournament catalogue**
on `main` for the first publication. The site URL and the privacy policy URL
are preserved. Catalogue scans are scheduled for 00:17, 08:17, 12:17, 16:17,
and 20:17 in Asia/Baku. If GitHub misses a run, the next scheduled scan can
pick up the latest results. For urgent publication, dispatch the workflow on
`main`. A push to `main` can run fixture checks. Site changes go out on the
next scheduled or manual deployment. The head-to-head indexes refresh daily.
Builds reuse the last successful indexes between refreshes and when a refresh
fails.

The version 1 document contains a `tournaments` array. Each entry includes the
numeric Results index ID, published name, `singles` or `doubles` format and ISO
start and end dates. The total prize and entrant count are optional. The count
is stored as `players` for singles and `pairs` for doubles. The generator
verifies competition format using the event page and published match pages.
For doubles events that publish only a player count, it counts distinct pairs
by player profile identity across the published results. A failed crawl
leaves the deployed site available.

The `firstListed` object maps every observed Results index ID to the Unix
timestamp in milliseconds of its first successful observation. It records
unverified events, team tournaments, and IDs that later leave the index. The
Pages job carries this history forward from the live catalogue. The first
publication with this field seeds it from the current index and any IDs in the
legacy catalogue. Publish the tracked catalogue before releasing Android with
new-tournament indicators. Android establishes its baseline on the first refresh
that includes this history, then persists acknowledgements by category and event ID.

An optional `podium` object records published `gold`, `silver`, and `bronze`
placements. Each placement lists its medalists by player ID and full name,
with a website-relative `photo` reference when available. Singles placements
come from the labeled event-page podium. Doubles placements come from the
order of pairs in the event's winners section, with partner names and portraits
resolved through the website's pair metadata. Older catalogue entries without
a podium still provide the tournament facts and match results.

Resolve gold, silver, and bronze separately for each verified doubles tournament.
When a pair lookup is unavailable or unusable, carry forward the last verified
medalists for the same Results index ID and medal while the source supplies no
evidence of a change. When the website identifies a different pair, publish
that medal once both players are verified. The generator records confirmed
website pair IDs and withheld medals in the top-level `doublesPodiumState`.
This lets later crawls keep a superseded medal hidden through further lookup
failures and remember the last verified placement if a tournament temporarily
leaves the catalogue. Legacy catalogues have player IDs but may lack pair IDs.
The generator uses player IDs when it can establish pair identity without a
stored pair ID. The version 1 podium still contains just the medalists for each medal.
Tournament cards and verified match results continue to publish with incomplete
podiums. Restoring or correcting a medal preserves the existing tournament
results publication and alert history.

For [issue #127](https://github.com/eshref/TableTennisAz/issues/127), dispatch
**Tournament catalogue** from `main` with **recover_doubles_podiums** checked.
The [recovery snapshot](../../scripts/tournament-catalogue/recovery/doubles-podiums-45130487896.json)
contains the 219 verified doubles podiums extracted from
[run 45130487896](https://github.com/eshref/TableTennisAz/actions/runs/45130487896).
The original `tournaments-v1.json` SHA-256 was
`d73ff63c05dcacadb8c3dd373ffc7436f92e64051e3530b71ad01e50abbfcb57`.
The snapshot supplies missing placements after the current catalogue and website
observations have been considered. The regular crawl verifies tournament
membership and matches, and the live catalogue supplies first-listed and
publication history. The recovery run summarizes coverage by format, accounts
for every historical doubles placement, and reports medalist IDs for tournament
941. Its publication job verifies the live JSON against that reviewed build.
After publication, refresh **Qoşa turnirlər** and check that tournament 941
shows the medalists reported in the run summary.
