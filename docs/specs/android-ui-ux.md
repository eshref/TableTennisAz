# Modernize the Android application's UI/UX

This specification's original refresh interval has been superseded by the
[48-hour freshness policy](../../README.md#freshness-and-offline-use).

## Problem Statement

The Android app presents rankings, player profiles, doubles profiles, rating
history, a results feed, and match details using basic native controls with weak
visual hierarchy. Large utility-button stacks consume space above meaningful
content. Ranking values run together, match summaries read like long sentences,
and photos, dark appearance, and a consistent component system are absent.
Loading, offline, empty, and failure states are difficult to distinguish.

Amateur players need to find themselves or other players, understand their
standing, and explore their matches without fighting the interface. They also
need the installed Android app to match the reviewed design, rather than relying
on the appearance of a browser prototype as evidence of native presentation.

## Solution

Apply the approved final design throughout the existing native Android app.
Use a navigation drawer for Rankings and Results, comfortable ranking rows with
circular profile photos, a restrained green design system, and automatic
light/dark appearance. Group each row's names and points together and show its
rank separately. For doubles, place both partners' portraits side by side inside
one circular avatar, with independent neutral silhouette fallbacks.

Keep singles rating history on its own screen and doubles history inline in the
doubles profile. Improve identity summaries, match cards, scoreboards, filters,
chart labels, feedback, and accessibility across all screens while preserving
existing features and data rules.

The seven approved entry views are Players rankings, Doubles rankings, Player
profile, Doubles profile, Results, Match details, and Singles rating history.
The two ranking views remain modes of the existing Rankings screen, rather
than becoming additional top-level destinations.

Acceptance requires all of the following:

- The native app reflects the approved design across all seven entry views.
- Drawer navigation, search, participant links, Back navigation, filters,
  pagination, and offline reuse continue to work through the redesigned views.
- Photos and their failures never block usable ranking content or corrupt
  player/pair identity, saved data, or successful-update times.
- Loading, empty, first-load failure, failed refresh with saved data, offline,
  and success states are clear and appropriate to the affected screen.
- Both themes, large text, narrow phones, landscape, and larger screens retain
  readable content and reachable actions.
- Actual screenshots from the installed Android app are compared with the
  approved HTML reference and supplied for review. Browser rendering and host
  simulation alone do not establish Android visual acceptance.
- Relevant integration checks, Android lint, and APK builds pass; device and
  live-source verification gaps are reported accurately.

The plan and seven browser views are approved. The current task authorizes
specification and publication only; application implementation requires a
separate user instruction.

## User Stories

1. As an amateur player, I want the app to open on Players rankings, so that I can immediately check the standing relevant to me.
2. As an amateur player, I want Rankings and Results in a clear navigation drawer, so that I can predictably move between the app's main destinations.
3. As a returning player, I want Rankings to retain my Players/Doubles selection, search, and scroll position when I return, so that I can continue browsing where I left off.
4. As a player exploring matches, I want Back to return through the match and profile screens I visited, so that I do not lose the context of my journey.
5. As a player scanning rankings, I want comfortable rows that group names and points with a separate rank badge, so that I can identify and compare entries quickly.
6. As a player with a long name or doubles partnership, I want full names to wrap readably, so that I can distinguish people and partnerships without relying on initials.
7. As an Azerbaijani-speaking player, I want name search to respect Azerbaijani case rules and match either doubles partner, so that I can find the intended entry reliably.
8. As a player searching rankings, I want matching entries to keep their real amateur rank or published doubles position, so that search does not misrepresent their standing.
9. As an amateur player, I want singles eligibility and tied ranks to stay correct, so that the redesign does not change who appears or what their position means.
10. As a player identifying someone in a ranking, I want their actual profile photo inside the circular avatar, so that I can recognize them visually.
11. As a doubles player, I want both partners' actual portraits inside one split circular avatar, so that the image identifies the partnership.
12. As a player viewing an entry without a photo, I want a neutral silhouette, so that missing imagery is clear and the layout remains consistent.
13. As a doubles player whose partner lacks a photo, I want only that partner's half to use a silhouette, so that the other available portrait remains visible.
14. As a returning offline player, I want previously downloaded usable photos to remain available, so that recognition does not depend on a fresh connection.
15. As a player on a slow connection, I want names, points, search, and navigation to work while photos load, so that images do not delay my main task.
16. As a player scrolling or switching selections, I want each photo to stay attached to the correct identity, so that delayed downloads never show another person's portrait.
17. As a player opening a profile, I want identity and current points presented clearly, so that I can understand whose record I am viewing and their current standing.
18. As a player reviewing performance, I want a visible Xal tarixçəsi action that opens a dedicated singles rating-history screen, so that the profile remains focused while the chart has sufficient space.
19. As a player browsing profile history, I want match cards with separated participants, scores, and metadata, so that I can understand an outcome before opening it.
20. As a player following a doubles partnership, I want each partner's name to open their own player profile, so that I can explore either person's individual record.
21. As a doubles player, I want the pair's own points, published position, medals, and ranking update time, so that partnership information is not confused with individual statistics.
22. As a doubles player, I want unknown medal counts distinguished from zero, so that unavailable information is not presented as an actual result.
23. As a doubles player reviewing history, I want the existing inline Xal/Mövqe controls and readable chart, so that I can switch between rating and ranking observations without leaving the profile.
24. As a doubles player interpreting the chart, I want the device-local coverage and first-observation or no-history state explained, so that I understand the limits of the recorded history.
25. As a player reading rating history, I want source dates and values preserved, so that missing years or unsupported observations are not invented for presentation.
26. As a player using a screen reader, I want chart observations available as accessible dates and values, so that the chart's meaning does not depend on seeing the plotted line.
27. As a player entering Results, I want to see the loaded results feed immediately, so that I can explore matches without first choosing an event.
28. As a player narrowing Results, I want All results, event-match, and duel selections to remain clear, so that I can browse the type of match I need.
29. As a player following an event, I want the event chooser to remain available, including events outside the initial general batch, so that I can reach that event's results.
30. As a player reading the results feed, I want its partial coverage explained without overwhelming the page, so that I do not mistake a downloaded batch for every recent match.
31. As a player exploring older results, I want Load more to remain visible where more source pages exist, so that I can extend the loaded history deliberately.
32. As a player refreshing a paginated selection, I want my loaded coverage and browsing context preserved, so that refreshing does not silently restart the journey.
33. As a player opening a match, I want a clear scoreboard grouping the two sides with their scores, so that I can immediately understand the recorded outcome.
34. As a player opening a doubles match, I want both players on each side linked to the correct profiles, so that every participant remains reachable.
35. As a player inspecting a match, I want its event, supplied date, and supplied time presented clearly, so that I understand when and where the result belongs without fabricated precision.
36. As a player waiting for data, I want visible progress and useful loading feedback, so that I know the app is working.
37. As a player waiting for the first doubles ranking, I want identity-resolution progress and other screens to remain usable, so that a long source load does not trap me.
38. As a player whose initial load fails, I want an honest unavailable state and a visible retry action, so that I can recover without mistaking the failure for empty data.
39. As a player whose refresh fails, I want usable saved content to remain visible with an explanatory notice, so that a temporary failure does not remove information I already have.
40. As an offline player, I want a calm saved-data notice rather than an unexplained refresh error, so that I understand what remains usable.
41. As an offline player opening an unseen selection, I want it identified as unavailable, so that missing saved data is not presented as a verified empty result.
42. As a player checking freshness, I want the last successful update time and clear refresh feedback, so that I can judge the information's age and whether my action completed.
43. As a player with no matching search or no available history, I want a specific empty state and an appropriate next action, so that I understand why the screen has no rows.
44. As a player using dark appearance, I want the app to follow my phone's system setting automatically, so that the interface fits my environment without separate configuration.
45. As a player moving between screens, I want consistent typography, spacing, colors, shapes, icons, and touch feedback, so that the app feels coherent and predictable.
46. As a player using larger text, I want names, scores, filters, and primary actions to remain readable and available, so that text scaling does not prevent me from using the app.
47. As a player with accessibility needs, I want sufficient contrast, clear labels and headings, and comfortable touch targets, so that essential information and actions are usable.
48. As a player on a small phone or in landscape, I want the layout to adapt and scroll appropriately, so that content and controls are not clipped or overlapped.
49. As a player using a larger screen, I want sensible content widths and spacing, so that the interface remains easy to scan.
50. As a player sensitive to motion, I want transitions to respect platform animation settings, so that feedback remains comfortable.
51. As a player updating the app, I want existing saved rankings, profiles, histories, and results to remain readable when photo metadata is absent, so that the redesign preserves offline continuity.
52. As the app owner, I want screenshots of all seven views from the actual Android build compared with the approved prototype, so that browser approval translates into native visual evidence.
53. As the app owner, I want a debug APK to inspect on my own phone, so that I can verify the experience on my device.
54. As the app owner, I want test results and device-verification limitations reported clearly, so that I can distinguish verified behavior from remaining gaps.

## Implementation Decisions

- **Existing architecture:** retain Java Activities and native Android Views, the current supported Android range, and direct public-website data access. Build shared styling and presentation components around the existing screen and saved-data infrastructure. Apply Material Design 3 principles where appropriate without a framework migration or unnecessary dependencies.
- **Design system:** derive the green light/dark palettes, type hierarchy, spacing, shapes, and interactive states from the approved final prototype. Use Android density-independent dimensions and scalable text. Follow the device's light/dark configuration; do not add an in-app theme settings page.
- **Navigation:** use a drawer on the two main destinations. Rankings remains the launch destination and opens on Players on a fresh launch. Keep Players/Doubles inside Rankings. Detail screens use normal Back navigation through the originating journey. Returning to Rankings retains the established selection, search, and scroll context; state remains coherent through supported Activity recreation.
- **Screen chrome:** replace full-width Back/Refresh button stacks with a compact, consistent title/action bar. Keep refresh discoverable, expose update time with secondary emphasis, and place error/offline/progress feedback where it does not displace the main content unnecessarily.
- **Ranking composition:** use the approved Comfortable rows structure: circular avatar, full name or both partner names, points beneath or beside the names, and a separate rank badge. Full names wrap; ranking movement remains readable with accessible directional meaning. Do not retain the earlier compact-row or full-player-card alternatives as product modes.
- **Singles profile:** present identity and current points clearly, keep match history prominent, and provide the dedicated Xal tarixçəsi action. A displayed amateur position must come from a known amateur ranking; do not manufacture positions or category membership for other linked profiles.
- **Doubles profile:** retain both linked partner identities, the pair's current saved ranking points and published position with its own timestamp, partnership medals, inline points/rank history, and paginated match history. Unknown medals remain distinct from zero and are never calculated by adding individual medals.
- **Results presentation:** keep the existing mixed results-feed behavior, source-type and event filters, partial-coverage explanation, and bounded Load more. Use structured match cards with separate names, scores, and metadata. Empty, unavailable, and partially loaded selections remain distinct.
- **Match presentation:** group each side's participants and score into a readable scoreboard. Every singles and doubles participant remains linked to the correct player profile. Show only source-supplied event/date/time information; visual emphasis must agree with the recorded outcome.
- **Charts:** improve visual labels and accessible observation values without changing data meaning. Singles rating history remains a separate destination and preserves source ordering and yearless dates. Doubles history remains an inline, switchable projection of existing device-local ranking observations, preserves chronological spacing, puts better rank numbers higher, and retains explicit first-point and no-history states. Profile refresh and chart-mode switching do not create ranking observations.
- **Photo acquisition:** preserve optional source-provided image URLs during parsing. Singles ranking HTML already supplies thumbnails; doubles metadata already supplies each partner's picture URL alongside identity through the public `player1PictureUrl` and `player2PictureUrl` fields. Reuse those existing responses and cached metadata where possible, and integrate optional photo discovery with refresh for older saved identities. Do not fetch every full player profile solely to obtain a ranking thumbnail or invent alternative image filenames.
- **Photo identity and composition:** show the actual player's portrait for singles. A doubles avatar is one circle divided side by side between its two partners' portraits; no independent pair photograph is required. Source photos may be cropped for display but must remain associated with their actual identities.
- **Photo fallback:** use a bundled neutral person silhouette when no usable photo is available. Each doubles half falls back independently. Keep an available partner portrait visible, and preserve a usable cached image through a failed new download. The fallback must not require a successful network request.
- **Photo loading and cache:** load/decode images asynchronously and retain usable downloaded images for offline reuse. Prioritize images needed for usable visible content rather than withholding the ranking until all images finish. Bind completed requests to the intended identity so recycled rows, searches, or selection changes cannot display a stale photo. Image failures must not fail ranking/profile data loads, overwrite usable data, or advance data-update timestamps.
- **Persistence compatibility:** add photo metadata compatibly and keep snapshots without such metadata readable. Keep image storage and failures independent of authoritative ranking, profile, results, and history saves. Reuse the existing HTTPS/security policy and normal certificate validation.
- **Saved-data contract:** display usable saved data immediately. Refresh missing data or data more than 15 minutes old when opened online; exactly 15 minutes remains fresh. Manual refresh works at any online age. Successful complete data and its update time save atomically; failed or unusable responses preserve both. Incremental Load more retains the established full-refresh timestamp. No periodic background-download schedule is introduced.
- **Ranking/domain contract:** singles use the website's Amateur membership and competition ranks, including players also listed as Professional; professional-only players remain outside singles rankings but can still appear through profiles, matches, and doubles. Doubles retain published pair positions, separate partnerships for different teammates, either-partner search, and the existing observation/movement comparison rules.
- **Feedback states:** distinguish initial loading, initial unavailability, failed refresh with saved content, offline saved content, verified empty history/results, no matching search, and successful refresh. Provide retry/clear-filter/clear-search actions where meaningful. Do not imply that a never-visited offline selection has verified empty data. Photos can have their own placeholders while the surrounding data remains usable.
- **Accessibility and adaptation:** retain full content and primary actions at large text sizes and narrow widths, provide at least 48dp effective touch targets, readable contrast, accessible headings/labels, and understandable chart and movement values. Do not convey outcome or movement by color alone. Use sensible content widths and scrolling in landscape/larger layouts. Keep animation subtle and respect platform motion settings.
- **Prototype boundaries:** the browser's view picker, state simulator, photo-availability controls, viewport chooser, and appearance/text inspection controls are review tools. They do not become product controls. Public photos and identities in the example do not make its illustrative match results, charts, movement, or timestamps production data.

## Testing Decisions

- **Approved seams:** reuse the already discussed native product UI plus real local HTTP fixture server as the primary integration seam. Drive actual Activities and production HTTP/parsing/persistence through the existing Robolectric setup. Complement that seam with the existing installed-APK journey and native screenshots for rendering/device evidence; do not introduce a separate application architecture or testing hooks merely to check the redesign.
- **Observable guarantees:** assert customer-visible text, rendered image/placeholder identity, accessible values, enabled actions, navigation outcomes, and externally captured HTTP requests. Saved files may be prepared or cleaned for setup, but assertions and polling must not rely on private serialized data, internal recording state, or helper implementation. Adapt selectors to the new view composition without weakening established guarantees or adding hidden legacy UI solely to satisfy old tests.
- **Prior art:** extend the existing rankings/profile/match/results workflows, doubles navigation/profile/history/movement workflows, freshness and failure workflows, and the opt-in installed-app experience/doubles journeys. Their current public UI and HTTP seams already exercise the relevant production boundaries. Use existing integration coverage where it already establishes the required guarantee, rather than adding duplicate unit tests.
- **Navigation and rendering:** exercise drawer switching, retained Players/Doubles/search/scroll state, row-to-profile navigation, separate singles chart navigation, participant links, Back context, Activity recreation, filters, and Load more using the redesigned controls. Verify full long names, two-player names, separated scores/points/ranks, and meaningful states across affected screens.
- **Photo integration:** supply valid fixture images through HTTP and verify correct player-to-photo and pair-to-partner binding in the product UI. Cover absent/null/default source photos, a single missing doubles partner, both missing, failed/invalid image responses, and retention of readable names/points/navigation during delayed image loads. Exercise delayed completion after row reuse or selection changes so a different identity never receives the old image.
- **Photo/offline lifecycle:** download photos through production behavior, recreate/restart and go offline, then verify visible cached images and independent fallbacks. Verify photo failure or recovery does not alter the authoritative data/update time. Exercise legacy saved snapshots with no photo metadata and acquisition of optional photos when online metadata becomes available. Test actual product scenarios, not the cache helper or fixture as a subject.
- **Preserved domain behavior:** retain existing coverage for amateur eligibility and ties, Azerbaijani case-aware search, published pair positions and movement, pair medals and unknown counts, yearless dates, device-local observations, source-verified participant identity, partial results, filter isolation, bounded/atomic pagination, refresh boundaries, and failure/offline preservation. Add only meaningful uncovered integration scenarios after inspecting coverage across suites.
- **Deterministic layout checks:** inspect both themes, standard/large text, long names and doubles names, populated/empty/loading/error/offline views, and portrait/narrow/landscape layouts. Keep runtime coverage for minimum API 26 and current target API 36 where the existing tooling supports it. Host-side success must not be reported as actual device installation or native visual acceptance.
- **Native visual acceptance:** run the built APK on Android and capture all seven approved entry views in light and dark appearance. Compare actual screenshots side by side with the approved reference using comparable content, available width, text scale, and density. Correct differences in hierarchy, spacing, shapes, typography, photos, cards, controls, and grouping before calling the design complete. Exercise narrow screens, large text, and landscape across those views and retain representative stress-state evidence. Android fonts and system bars may vary, but those differences do not excuse clipped content, unreachable controls, wrong imagery, or materially different layout.
- **Delivery checks:** run the existing application integration suite, Android lint, debug APK assembly, and instrumentation APK assembly. Exercise the installed-app journeys against appropriate fixtures/live public sources where available. Provide actual Android screenshots and a debug APK for the owner's phone. Report exact devices/APIs, screen/text/theme settings, commands and results, and any unperformed physical-phone, current-device, or live-source checks. Do not claim a phone was tested solely because the APK was produced.
- **Test quality:** use the repository's Java/JUnit/AssertJ conventions, clear Arrange/Act/Assert phases, and parameterization for genuinely parallel scenarios. Do not add tests for prototype controls or test infrastructure. Preserve combined lifecycle/workflow tests when their interactions provide distinct coverage.

## Out of Scope

- Application implementation during this specification task; coding requires a later explicit instruction.
- New favorites, accounts, login, reservations, social functions, or additional top-level destinations.
- A user-facing theme-settings screen, density picker, prototype view switcher, or mock-state controls.
- A framework migration, embedded-web replacement for the native app, new backend/hosted adapter, unnecessary architectural changes, or unnecessary dependencies.
- Changes to amateur eligibility, ranking/points semantics, pair membership, medals, match outcomes, source dates, results completeness, pagination boundaries, or the existing data refresh policy.
- Global doubles history, backfilled observations, a complete globally latest results service, or unsupported ranking/history claims.
- Requiring a dedicated photograph of a doubles partnership, generating substitute portraits, or introducing photo uploads/editing.
- Shipping illustrative prototype matches, chart observations, movement, timestamps, or embedded public sample records as hardcoded production data.
- Pixel-identical system chrome on every Android device, universal device certification, or treating the owner's physical-phone check as already completed.
- Google Play publication, production signing, deployment, or remote Git pushes.

## Further Notes

- Published specification: https://github.com/eshref/TableTennisAz/issues/23.
- The owner approved the plan after reviewing all seven browser entry views and discussing verification on Android. The requested next step is specification publication only, with no application implementation yet.
- The [approved final visual reference](../prototypes/android-final-ui-prototype.html) is available as standalone HTML. The navigation and ranking-density comparison files record the considered alternatives; the selected direction is Navigation drawer plus Comfortable rows with actual photos. Prototype sample scores/history/movement are illustrative even where identities, portraits, ranking values, or medals came from public data.
- The final standalone HTML was verified by opening it directly with networking disabled, including all seven entry points and seven example states, portrait decoding, split-photo fallbacks, navigation, search, charts, filters, retry/success feedback, keyboard/view switching, 320px dark/large-text layouts, and landscape. No external HTTP requests or JavaScript exceptions were observed. These are prototype checks, not Android implementation results.
- Existing architecture decisions remain in force: native Android screens, direct public-website access, revised amateur-only singles rankings, Results as a partial recent-results feed, and device-local doubles observations. The redesign does not supersede those decisions.
- Readiness describes how completely this work is specified; the `ready-for-agent` label does not authorize an agent to start implementing before the owner requests it.
