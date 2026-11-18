# Approved-reference fixtures

`reference.json` copies the public identities, points, positions and partnership
medals from `docs/prototypes/android-final-ui-prototype.html` at
`809029976d182934bdc4ba2a9dcfd60acb8adf40`. Portrait bytes are extracted from that
same self-contained reference. Four identical portraits reuse `../portraits/`;
the other six are in `portraits/`. The reference has no portrait for players
14 and 457, so they use independent bundled silhouettes.

`NativeAcceptanceDeviceTest` prepares saved data with these values. Match IDs,
match results and chart observations are illustrative; this is a deterministic
installed navigation/visual check, not a live website download. Setup timestamps
use the current time to avoid an automatic stale refresh during the first journey.
Later configurations reuse the same snapshots after force-stop, without rewriting
them. The fixture assets are included only in the instrumentation APK.

The additional guarantee is one Android task-stack journey connecting Rankings,
both profiles, both history presentations, Results, match details and participant
navigation, retaining per-mode search and inline history selection on return.
Existing screen-specific tests retain the longer-name, all-participant,
independent-photo-failure and real HTTP acquisition coverage.
