## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Players can open Rankings, move between Rankings and Results using the approved
navigation drawer, and use a compact main-screen action bar with clear refresh,
loading, offline, and failure feedback. The main destinations adopt the approved
green system light/dark presentation while preserving existing data and journeys.

Introduce the minimum shared styling and screen-construction seams additively
inside this working journey. Existing detail screens remain usable until their
own redesign slices land; no standalone infrastructure refactor or framework
migration is required.

## Acceptance criteria

- [ ] A fresh launch opens Rankings in Players mode. The drawer exposes Rankings and Results, shows the active destination, and closes through its close action or Android Back before leaving the current destination.
- [ ] Switching destinations and returning preserves the established Players/Doubles selection, search, and scroll context. Existing result selections, row links, detail-screen navigation, and Back behavior continue to work without accumulating redundant destination screens.
- [ ] Main screens use the approved compact title/action bar, discoverable refresh, readable successful-update time, consistent spacing and touch feedback, and automatic system light/dark appearance.
- [ ] Main-screen states distinguish initial loading, initial failure with retry, failed refresh with saved content, offline saved content, no matching search or verified empty results, and successful refresh. An unseen offline selection remains unavailable rather than verified empty.
- [ ] Preserve saved-data-first rendering, the more-than-15-minute refresh threshold, manual refresh at any online age, atomic successful saves, and update-time/content preservation after failure. Keep other screens usable during doubles identity loading.
- [ ] Shared presentation seams are introduced compatibly so unaffected screens and their source-data behavior remain usable while later tickets migrate them. Reuse the existing native Views, Activities, options for accessibility, and source integration.
- [ ] Drawer and main-screen controls are labeled, comfortably tappable, and readable in both themes with large text and narrow/landscape layouts. No prototype-only controls become product settings.
- [ ] Exercise drawer navigation, retained ranking context, source-backed refresh/state transitions, and supported Activity recreation through existing native-UI/HTTP-fixture integration seams. Reuse existing freshness/failure guarantees and add only distinct uncovered scenarios.
- [ ] Capture the affected main-screen chrome on Android and compare it with the approved visual direction. The existing relevant integration checks and Android lint remain green; do not defer this slice's behavior checks to final delivery.

## Blocked by

None (can start immediately after implementation is separately authorized).
