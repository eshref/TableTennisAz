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
