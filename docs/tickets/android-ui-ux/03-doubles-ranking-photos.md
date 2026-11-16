## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Players browse the doubles ranking using Comfortable rows with both full partner
names, pair points, a separate published-position badge, and one split circular
avatar containing the partners' actual portraits. Either half falls back to a
neutral silhouette independently, and downloaded portraits remain usable offline.

## Acceptance criteria

- [ ] Doubles ranking uses the approved row structure and one side-by-side split circular avatar per partnership. A missing portrait affects only its half; the other usable portrait remains visible. Both missing portraits display two independent silhouettes.
- [ ] Read optional partner-photo URLs from the same public doubles metadata already used for identity. Existing cached identities without photo fields stay readable and can acquire optional photo metadata online without requiring a dedicated pair photograph.
- [ ] Reuse the singles slice's image loading/cache behavior, preserve correct partner-to-half identity through row recycling and selection changes, and avoid withholding complete usable ranking data while portraits download.
- [ ] Keep published pair positions even when points tie, distinct partnerships for different teammates, full names, either-partner Azerbaijani search, preserved search/scroll/mode context, and correct pair-profile navigation.
- [ ] Preserve first-load identity-resolution progress, resumable identity discovery, usable other screens, saved-data behavior, and atomic complete ranking refreshes. Image failures cannot advance ranking timestamps or replace the saved ranking.
- [ ] Retain existing movement semantics and dated observations, including accessible direction/value, preservation after failure/offline restart, no first-observation movement, and clearing movement after an unchanged successful refresh.
- [ ] Through native product UI and real HTTP fixtures, verify two portraits, each one-missing case, both missing, absent fields on old identities, independent image failure, delayed image completion after changing selection, and offline reuse without cross-partnership photo leakage. Reuse established ranking/movement coverage where sufficient.
- [ ] Check long two-player names, movement and position readability, touch targets, large text, both themes, and narrow/landscape layouts. Capture the native Doubles ranking against the approved reference and keep relevant checks green.
