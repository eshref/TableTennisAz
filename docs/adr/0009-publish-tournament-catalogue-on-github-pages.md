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
are preserved. Later updates run daily and on changes to the site or generator.

The version 1 document contains a `tournaments` array. Each entry includes the
numeric Results index ID, published name, `singles` or `doubles` format and ISO
start and end dates. The total prize and entrant count are optional. The count
is stored as `players` for singles and `pairs` for doubles. The generator
verifies competition format using the event page and published match pages.
For doubles events that publish only a player count, it counts distinct pairs
by player profile identity across the published results. A failed crawl
leaves the deployed site available.
