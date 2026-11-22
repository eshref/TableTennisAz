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
are preserved. Updates run every 30 minutes and on changes to the site or
generator. The head-to-head indexes refresh daily. Builds reuse the last
successful indexes between refreshes and when a refresh fails.

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
