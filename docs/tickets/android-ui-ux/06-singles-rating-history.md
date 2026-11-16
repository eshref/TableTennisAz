## Parent

Part of [spec #23](https://github.com/eshref/TableTennisAz/issues/23).

## What to build

Players open a clear dedicated singles rating-history screen with readable chart
labels and accessible observation values, then return to their player profile.
The screen remains useful with missing history, saved data, and offline access.

This slice can start after the shared screen presentation exists; the current
player profile already exposes the history action, so its separate visual
redesign does not gate this work.

## Acceptance criteria

- [ ] Retain the dedicated Xal tarixçəsi destination and normal Back journey to the correct player. Use the approved compact detail chrome and consistent light/dark presentation.
- [ ] Show readable chart labels and source date/value observations, with accessible text equivalents and useful visual hierarchy. Data meaning does not depend only on seeing the line or on color.
- [ ] Preserve the website's observation values, decimal presentation, order, and date labels. Do not invent missing years, dates, chronological precision, or observations.
- [ ] Handle a single observation and genuinely absent history honestly. Keep missing history distinct from initial unavailability, and preserve cached history/update times when refresh fails or the device is offline.
- [ ] Preserve the existing saved-data-first and refresh rules; presentation or observation inspection must not introduce unsolicited data mutations or a background download schedule.
- [ ] Exercise the production rating-history response through the existing native-UI/HTTP-fixture workflow, including accessible values, source order/yearless dates, no-history behavior, navigation, and offline reuse. Extend only uncovered guarantees.
- [ ] Verify chart/observation readability and available actions with large text, both themes, narrow width, and landscape. Capture the native rating-history screen against the approved reference and keep relevant checks green.
