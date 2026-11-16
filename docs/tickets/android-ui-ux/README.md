# Android UI/UX implementation tickets

The owner approved this nine-ticket breakdown for [spec #23](https://github.com/eshref/TableTennisAz/issues/23).
All tickets are published with the `ready-for-agent` label and native GitHub
sub-issue relationships to #23. Blocking relationships are native GitHub edges.

| Ticket | Local acceptance criteria | Blocked by |
| --- | --- | --- |
| [#24: Drawer navigation and main-screen layout](https://github.com/eshref/TableTennisAz/issues/24) | [Acceptance criteria](01-drawer-main-screen-layout.md) | None |
| [#25: Comfortable player rankings with cached profile photos](https://github.com/eshref/TableTennisAz/issues/25) | [Acceptance criteria](02-player-ranking-photos.md) | [#24](https://github.com/eshref/TableTennisAz/issues/24) |
| [#26: Comfortable doubles rankings with split portraits](https://github.com/eshref/TableTennisAz/issues/26) | [Acceptance criteria](03-doubles-ranking-photos.md) | [#25](https://github.com/eshref/TableTennisAz/issues/25) |
| [#27: Results feed match cards and filters](https://github.com/eshref/TableTennisAz/issues/27) | [Acceptance criteria](04-results-feed-cards.md) | [#24](https://github.com/eshref/TableTennisAz/issues/24) |
| [#28: Player profile and match-history journey](https://github.com/eshref/TableTennisAz/issues/28) | [Acceptance criteria](05-player-profile.md) | [#25](https://github.com/eshref/TableTennisAz/issues/25), [#27](https://github.com/eshref/TableTennisAz/issues/27) |
| [#29: Dedicated singles rating-history screen](https://github.com/eshref/TableTennisAz/issues/29) | [Acceptance criteria](06-singles-rating-history.md) | [#24](https://github.com/eshref/TableTennisAz/issues/24) |
| [#30: Match scoreboard and participant navigation](https://github.com/eshref/TableTennisAz/issues/30) | [Acceptance criteria](07-match-details.md) | [#25](https://github.com/eshref/TableTennisAz/issues/25) |
| [#31: Doubles profile, medals, and inline history](https://github.com/eshref/TableTennisAz/issues/31) | [Acceptance criteria](08-doubles-profile.md) | [#26](https://github.com/eshref/TableTennisAz/issues/26), [#27](https://github.com/eshref/TableTennisAz/issues/27) |
| [#32: Native visual acceptance and debug APK delivery](https://github.com/eshref/TableTennisAz/issues/32) | [Acceptance criteria](09-native-acceptance-apk.md) | [#28](https://github.com/eshref/TableTennisAz/issues/28), [#29](https://github.com/eshref/TableTennisAz/issues/29), [#30](https://github.com/eshref/TableTennisAz/issues/30), [#31](https://github.com/eshref/TableTennisAz/issues/31) |

The dependencies follow actual shared capabilities. #24 introduces compatible
presentation seams inside a working drawer journey; #25 supplies the photo
pipeline; #26 adds split partnership imagery; #27 supplies shared match cards.
The current player profile already opens singles history, so #29 does not wait
for #28. Existing callers already open match details, so #30 does not wait for
the feed/profile redesigns. The doubles profile #31 does not wait for the
separate singles chart. #32 lists the remaining leaf blockers; earlier shared
and ranking work is already included transitively.

Execution frontier: #24 has no blockers. After #24, #25, #27, and #29 can
progress independently. After #25, #26 and #30 become available; #28 also
requires #27. #31 requires #26 and #27. #32 follows #28, #29, #30, and #31.

Every feature slice includes its own native-UI/real-HTTP product integration
scenarios and visual/accessibility/adaptation checks. Existing tests can satisfy
a guarantee; add coverage only for distinct gaps. #32 adds integrated installed
Android screenshot comparison and APK delivery, not all feature testing.

The approved visual primary source is the final standalone HTML, captured with
its alternatives on local branch `prototype/android-ui-ux-approved`, commit
`8cd6a1999d6a4522332249e7a56175989b08655a`. The parent spec records all approved
choices and distinguishes real public portraits from illustrative match/history
data. The prototype branch remains local and has not been pushed.

Publication verification confirmed all nine issue bodies match these local
ticket files, every ticket has the intended label and parent, all native
blocking edges match the approved graph, and the parent specification's title,
body, state, labels, and comments are unchanged. No application implementation
or remote Git push is authorized by ticket approval/publication.
