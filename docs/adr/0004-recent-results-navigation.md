# Open Results as a recent-match feed

The main menu now opens singles tournaments, doubles tournaments, and duels
directly. This approved navigation supersedes the combined Results entry
described here. [Issue #70](https://github.com/eshref/TableTennisAz/issues/70)

After comparing interactive prototypes, the project owner chose a recent-results
feed with an optional event filter for Q14. This makes match results directly
visible on entering Results, with links to match details and player profiles.
The alternative required choosing an event before viewing matches. Rankings
remains the app's launch screen.

The feed includes event matches and duels, with an All results / Tournament
matches / Duels selection filter. Event matches can also be filtered by event.

The website's inspected results navigation is event-based, so a cross-event
feed requires aggregation when accessing it directly from Android. A complete
recent-match API has not been identified. Load results in batches with a Load
more button. The first batch is partial and may not include every recent match;
batch sizes remain to be determined. The prototype's sample data does not
establish production completeness. The app shows saved data first. Opening a
screen online fetches missing data or refreshes it when more than 48 hours have
passed since its last successful update. Data exactly 48 hours old is still
fresh. Manual refresh is available at any age when online. Each Results selection
has its own last successful update time. A previously unloaded tournament
selection can reuse an event catalog up to 48 hours old.
