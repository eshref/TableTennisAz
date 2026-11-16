package az.tabletennis;

import android.app.Activity;

enum SavedScreenCase {
    PAIR(PairResultsActivity.class, "pairId", 110, "/api/getDuoInfo?id=110", "\"firstPlaceCount\":4", "\"firstPlaceCount\":7",
            "Qızıl medal: 4", "Qızıl medal: 7"),
    PROFILE(ProfileActivity.class, "playerId", 12, "/players/12", "İlqar Həsənov", "Yenilənmiş oyunçu", "İlqar Həsənov", "Yenilənmiş oyunçu"),
    MATCH(MatchActivity.class, "matchId", 501, "/matches/501", ">3</div>", ">4</div>", "3-2", "4-2"),
    CHART(ChartActivity.class, "playerId", 12, "/api/getPlayerPoints?id=12", "435.55002", "999", "31.12 — 435.55 xal", "31.12 — 999 xal"),
    RESULTS(ResultsActivity.class, "", 0, "/matches/results/event/901", "Kubok A", "Yeni Kubok A",
            "14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir", "14 Nov. 2026 · Yeni Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir");

    final Class<? extends Activity> activity;
    final String identity;
    final long id;
    final String path;
    final String oldFragment;
    final String newFragment;
    final String initialText;
    final String changedText;

    SavedScreenCase(Class<? extends Activity> activity, String identity, long id, String path, String oldFragment,
                    String newFragment, String initialText, String changedText) {
        this.activity = activity;
        this.identity = identity;
        this.id = id;
        this.path = path;
        this.oldFragment = oldFragment;
        this.newFragment = newFragment;
        this.initialText = initialText;
        this.changedText = changedText;
    }
}
