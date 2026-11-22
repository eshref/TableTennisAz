package az.tabletennis;

import java.util.Map;

final class HeadToHeadFixtures {
    private HeadToHeadFixtures() {}

    static final String ASHRAF = "Əşrəf Səfərov";
    static final String ILQAR = "İlqar Həsənov";
    static final String OLDER = "10 Aug. 2021 · İlqar Həsənov — Əşrəf Səfərov · 3-2 · Shua Open Cup #1 · Tək · Turnir";
    static final String NEWER = "16 Nov. 2026 · Əşrəf Səfərov — İlqar Həsənov · 1-3 · Duello · Tək · Duel";

    static final String MIDDLE = "14 Nov. 2026 · İlqar Həsənov — Əşrəf Səfərov · 3-0 · Liqa · Tək · Turnir";

    static void publish(Map<String, String> website) {
        website.put("/search?query=s%C9%99f", directory(15, ASHRAF));
        website.put("/search?query=ilq", directory(13, ILQAR));
        String older = row(46, "10 Aug. 2021", ILQAR, ASHRAF, "3-2", "Shua Open Cup #1");
        String namesake = row(503, "17 Nov. 2026", ASHRAF, ILQAR, "4-2", "Liqa");
        String doubles = row(504, "18 Nov. 2026", ASHRAF + "</div><div>Əli Məmmədov",
                ILQAR + "</div><div>Şəms Quliyeva", "3-2", "Liqa");
        website.put("/matches/results/player/13", history(older + namesake + doubles)
                + "<ul class='pagination'><li><a href='/matches/results/player/13?page=2'>2</a></li></ul>");
        website.put("/matches/results/player/15", history(older + row(402, "14 Nov. 2026", ILQAR, ASHRAF, "3-0", "Liqa")));
        website.put("/matches/46", details(13, ILQAR, 15, ASHRAF, "10 Aug. 2021", 3, 2, "Shua Open Cup #1"));
        website.put("/matches/502", details(15, ASHRAF, 13, ILQAR, "16 Nov. 2026", 1, 3, "Duello"));
        website.put("/matches/402", details(13, ILQAR, 15, ASHRAF, "14 Nov. 2026", 3, 0, "Liqa"));
        website.put("/matches/503", details(115, ASHRAF, 13, ILQAR, "17 Nov. 2026", 4, 2, "Liqa"));
        website.put("/matches/results/player/13?page=2", laterPage());
    }

    static String laterPage() {
        return history(row(502, "16 Nov. 2026", ASHRAF, ILQAR, "1-3", "Duello"));
    }

    private static String directory(long id, String name) {
        return "<div id='center'><div class='ranking-page-list'><a href='/players/" + id
                + "'><div class='rank-name'>" + name + "</div></a></div></div>";
    }

    private static String history(String rows) { return "<div id='center'><div class='matches-box'>" + rows + "</div></div>"; }

    private static String row(int id, String day, String left, String right, String score, String event) {
        return "<div><div class='divs-header'>" + day + "</div><div><a href='/matches/" + id + "'><div class='match-box'>"
                + "<div class='col-3'><div>" + left + "</div></div><div class='col-2'><div class='center'>" + score
                + "</div></div><div class='col-3'><div>" + right + "</div></div><div class='col-4'>" + event + "</div></div></a></div></div>";
    }

    private static String details(long leftId, String left, long rightId, String right, String day, int leftScore, int rightScore, String event) {
        return "<div id='center'><div class='match-players-box'><div class='match-player-box'><a href='/players/" + leftId
                + "'><div class='match-player-name'>" + left + "</div></a></div><div class='match-date'>" + day
                + "</div><div class='match-tournament'>" + event + "</div><div class='match-player-score'>" + leftScore
                + "</div><div class='match-player-score'>" + rightScore + "</div><div class='match-player-box'><a href='/players/"
                + rightId + "'><div class='match-player-name'>" + right + "</div></a></div></div></div>";
    }
}
