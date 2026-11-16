## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Deliver the complete redesigned Android experience as an installable debug APK,
with actual Android screenshots showing that all seven approved entry views
match the accepted design and behave correctly together. Supply clear evidence
and limitations so the owner can review the APK on their own phone.

Earlier slices already include their own behavior/integration/visual checks.
This ticket closes cross-screen consistency, packaged-app, and final delivery
acceptance rather than postponing feature testing until the end.

## Acceptance criteria

- [ ] Install and run the built Android APK and capture all seven approved entry views in light and dark appearance: Players rankings, Doubles rankings, Player profile, Doubles profile, Results, Match details, and Singles rating history.
- [ ] Compare actual native screenshots side by side with the approved final prototype using comparable content, available width, text scale, and density. Fix material differences in hierarchy, typography, spacing, shapes, photos, cards, grouping, and controls. Do not treat browser or host-only rendering as native visual acceptance.
- [ ] Exercise narrow phones, large text, and landscape across the seven entry views, retaining representative stress-state evidence. No essential names, scores, filters, chart observations, participant links, or actions are clipped, overlapped, or unreachable. Explain expected device-dependent font/system-bar differences without excusing product layout defects.
- [ ] Verify full installed-app journeys through drawer switching, ranking modes/search, both profile types, separate singles history, inline doubles history, match details, participant profiles, and Back context. Preserve the approved native structure without introducing browser prototype inspection controls into the product.
- [ ] Verify representative loading, empty, first-load failure, failed saved-data refresh, offline/restart, successful refresh, and independent photo-fallback behavior in the integrated product. Reuse the earlier fixture workflows and add only uncovered cross-screen/lifecycle guarantees.
- [ ] Confirm touch targets, readable contrast, semantic headings/labels, accessible chart/movement values, system light/dark behavior, and restrained motion across the integrated screens.
- [ ] Run the complete relevant native application integration suite, Android lint, debug APK assembly, and instrumentation APK assembly. Preserve existing API 26/API 36 coverage and report what ran on actual Android versus host simulation.
- [ ] Run the existing opt-in installed/live-source journeys where the environment supports them; report unavailable devices, inaccessible live sources, and any unperformed current-device or physical-phone checks accurately.
- [ ] Supply the debug APK, actual Android screenshots, comparison/verification notes, and remaining limitations for the owner's phone review. Do not claim that producing an APK establishes physical-phone verification or universal pixel-identical presentation.
- [ ] Keep existing domain, pagination, freshness, atomic-save, source identity, and offline guarantees intact. No production publication, signing rollout, remote push, or new product feature is authorized by this delivery ticket alone.
