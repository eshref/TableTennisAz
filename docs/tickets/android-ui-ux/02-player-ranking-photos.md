## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Players browse and search the amateur ranking in the approved Comfortable rows:
a circular actual profile photo, full name and points grouped together, and a
separate amateur-rank badge. Photos load without delaying names or navigation,
use a local silhouette when needed, and remain usable from cache offline.

This slice delivers the photo acquisition/display/cache behavior through the
singles-ranking journey, making it reusable by subsequent photo-bearing screens.

## Acceptance criteria

- [ ] Players ranking uses the approved Comfortable rows with readable wrapping full names, current points, actual circular portraits, and separate rank badges in both themes.
- [ ] Preserve amateur membership, exclusion of professional-only players, competition ties, Azerbaijani case-aware search, unchanged ranks during search, selected-player identity, scroll context, and existing row-to-profile navigation. Doubles mode remains functional before its own row redesign lands.
- [ ] Read optional thumbnail URLs already supplied by ranking responses. Do not invent alternative filenames or fetch every full player profile solely for ranking photos.
- [ ] Fetch/decode images asynchronously and retain usable downloaded images for offline reuse. Visible names, points, search, navigation, progress, and other screens remain responsive while photos are delayed.
- [ ] Missing, default-placeholder, invalid, or failed image responses use a bundled neutral person silhouette when no usable photo is cached. A failed new download preserves a usable cached photo.
- [ ] Completed image requests remain bound to the intended player through row recycling, search changes, mode changes, and supported Activity recreation. A delayed image never appears on another player's row.
- [ ] Optional image metadata and image failures do not invalidate older saved rankings, fail a ranking refresh, overwrite authoritative data, or change its successful-update time. Reuse the existing HTTPS policy and normal certificate validation.
- [ ] Through product UI plus real HTTP fixtures, verify image/identity binding, independent image failure, delayed completion after row reuse or selection change, successful offline image reuse after restart, and legacy snapshots lacking photo metadata. Assert rendered UI and external requests rather than cache internals.
- [ ] Verify long names, large text, accessible row values, touch targets, both themes, and narrow/landscape layouts. Capture the native Players ranking against the approved reference and keep relevant integration checks and lint green.
