The `legacy-duo-110`, `legacy-doubles-ranking`, and `legacy-duo-results-110`
binary fixtures are Java `SavedData` snapshots serialized using the unmodified
production classes at commit `48c903a`, before pair profiles had medal fields.
They exercise update compatibility through the Android screens, without using
current serializers to stand in for an older installation.

All were saved at 2026-11-15 12:00 UTC. Pair 110 consists of İlqar Həsənov (12)
and Əli Məmmədov (13), with published position 2 and 800 points. The saved history
contains match 501 against Şəms Quliyeva / Işıl Əliyeva (3-2, Kupa, 15 Nov. 2026),
the loaded first results page, and a continuation to page 2. No medals are stored.

`legacy-profile-12` was serialized with the unchanged `PlayerProfile`,
`MatchSummary`, and `SavedData` classes at `f8758d4`, before profile photo and
amateur-position fields existed. It contains İlqar Həsənov (12), 800 points,
match 501, and the first loaded history page at 2026-11-15 12:00 UTC.
