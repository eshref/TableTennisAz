## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Opening a player shows a polished identity/photo and current-points summary,
a visible Xal tarixçəsi action to the existing separate history screen, and
readable paginated match-history cards. Following a match or returning Back
preserves the selected player's identity and originating ranking context.

## Acceptance criteria

- [ ] Present the correct player's portrait or local silhouette, full name, and current points with the approved hierarchy and theme. Acquire optional profile-photo metadata through existing relevant responses and reuse the shared photo/cache behavior without blocking profile data.
- [ ] Any displayed amateur position comes from known amateur-ranking data. Do not invent positions, points, or category membership for professional or otherwise linked profiles absent from that ranking; unavailable values are explicit.
- [ ] Keep Xal tarixçəsi clearly visible and opening the dedicated singles rating-history destination. Do not move the chart inline or require completion of its later styling ticket for navigation to work.
- [ ] Reuse the Results slice's match cards for source-verified singles/doubles history, preserving coverage messaging, older-page Load more, correct match identities, and selected-player attribution even when names repeat.
- [ ] Following a match/participant and returning Back restores the player profile and originating ranking mode/search/scroll context. Preserve loaded history coverage across refreshes and supported recreation.
- [ ] Distinguish absent history, initial unavailability, loading, saved refresh failure, offline saved data, and successful refresh. Preserve the existing refresh boundary, update times, and cached data compatibility.
- [ ] Verify profile identity/points/photo, shared cards, chart action, participant/match navigation, compatible saved data, and offline restart through existing native product UI and real HTTP fixtures. Use existing complete workflows where they already establish the guarantee.
- [ ] Check long names, accessible headings/actions, large text, photo fallback, both themes, and narrow/landscape layouts. Capture native Player profile against the approved reference and keep relevant checks green.
