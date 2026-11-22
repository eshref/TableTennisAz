package az.tabletennis;

import java.util.Map;

final class HeadToHeadFixtures {
    private HeadToHeadFixtures() {}

    static final String ASHRAF = "Əşrəf Səfərov";
    static final String ILQAR = "İlqar Həsənov";
    static final String OLDER = "10 Aug. 2021 · İlqar Həsənov — Əşrəf Səfərov · 3-2 · Shua Open Cup #1 · Tək · Turnir";
    static final String NEWER = "16 Nov. 2026 · Əşrəf Səfərov — İlqar Həsənov · 1-3 · Duello · Tək · Duel";
    static final String ARCHIVE_PAGE_TWO_DUEL = "15 Nov. 2026 · Əşrəf Səfərov — İlqar Həsənov · 3-0 · Duello · Tək · Duel";
    static final String ARCHIVE_FINAL_DUEL = "13 Nov. 2026 · İlqar Həsənov — Əşrəf Səfərov · 3-1 · Duello · Tək · Duel";

    static final String MIDDLE = "14 Nov. 2026 · İlqar Həsənov — Əşrəf Səfərov · 3-0 · Liqa · Tək · Turnir";
    static final String ARCHIVE_TOURNAMENT = "18 Nov. 2026 · Əşrəf Səfərov — İlqar Həsənov · 3-1 · Liqa · Tək · Turnir";
    static final String ARCHIVE_TEAM_GAME = "12 Nov. 2026 · İlqar Həsənov — Əşrəf Səfərov · 2-3 · Komanda Liqası · Tək · Turnir";
    private static final String EMPTY_TOURNAMENT_ARCHIVE = "<div id='center'><div class='tournament-list'></div></div>";
    static final String ERHAN = "Erhan Əliyev";
    static final String ELCHIN = "Elçin Mustafayev";
    static final String ALIKHAN = "Əlixan Əlizadə";
    static final String KHAYAL = "Xəyal Həşimli";
    static final String FIRDOVSI = "Firdovsi Kərimov";
    static final String VAQIF = "Vaqif Babayev";
    static final String SHAKIR = "Şakir Qədirov";
    static final String DOUBLES = "09 Nov. 2026 · Erhan Əliyev / Elçin Mustafayev — Xəyal Həşimli / Əlixan Əlizadə"
            + " · 3-2 · Ping Pong Club Qoşa Reytinq Turniri · Qoşalar · Turnir";
    static final String DOUBLES_NEWER = "20 Nov. 2026 · Əlixan Əlizadə / Xəyal Həşimli — Elçin Mustafayev / Erhan Əliyev"
            + " · 3-1 · Liqa · Qoşalar · Turnir";
    static final String DOUBLES_TEAM_GAME = "10 Nov. 2026 · Erhan Əliyev / Elçin Mustafayev — Əlixan Əlizadə / Xəyal Həşimli"
            + " · 3-0 · Komanda Liqası · Qoşalar · Turnir";
    static final String HISTORICAL = "25 Mar. 2026 · Xəyal Həşimli / Firdovsi Kərimov — Vaqif Babayev / Şakir Qədirov"
            + " · 3-1 · BTTC Qoşa Reytinq Turniri #105 · Qoşalar · Turnir";

    static void publishEmptySingles(Map<String, String> website) {
        publishPlayer(website, "s%C9%99f", 15, ASHRAF);
        publishPlayer(website, "ilq", 13, ILQAR);
        website.put("/matches/results", EMPTY_TOURNAMENT_ARCHIVE);
        website.put("/matches/duellos", history(""));
    }

    static void publish(Map<String, String> website) {
        website.putIfAbsent("/matches/results", EMPTY_TOURNAMENT_ARCHIVE);
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

        String namesakeDuel = row(505, "15 Nov. 2026", ASHRAF, ILQAR, "4-2", "Duello");
        String doublesDuel = row(507, "14 Nov. 2026", pair(ASHRAF, "Əli Məmmədov"),
                pair(ILQAR, "Şəms Quliyeva"), "3-2", "Duello");
        String archiveOnlyDuel = row(508, "15 Nov. 2026", ASHRAF, ILQAR, "3-0", "Duello");
        website.put("/matches/duellos", history(overlappingDuelRow()) + duelPagination(2, 3));
        website.put("/matches/duellos?page=2", history(archiveOnlyDuel + namesakeDuel + doublesDuel) + duelPagination(3));
        website.put("/matches/duellos?page=3", history(row(506, "13 Nov. 2026", ILQAR, ASHRAF, "3-1", "Duello")));
        website.put("/matches/505", details(115, ASHRAF, 13, ILQAR, "15 Nov. 2026", 4, 2, "Duello"));
        website.put("/matches/506", details(13, ILQAR, 15, ASHRAF, "13 Nov. 2026", 3, 1, "Duello"));
        website.put("/matches/507", details(player(15, ASHRAF) + player(7, "Əli Məmmədov"),
                player(13, ILQAR) + player(8, "Şəms Quliyeva"), "14 Nov. 2026", 3, 2, "Duello"));
        website.put("/matches/508", details(15, ASHRAF, 13, ILQAR, "15 Nov. 2026", 3, 0, "Duello"));
    }

    static void publishTournamentArchive(Map<String, String> website) {
        website.put("/matches/results", "<div id='center'><div class='tournament-list'>"
                + "<a href='/matches/results/event/911'><div class='tournament-title'>Liqa</div></a>"
                + "<a href='/matches/results/event/912'><div class='tournament-title'>Klub turniri</div></a></div></div>");
        website.put("/matches/results/event/911", history(row(610, "18 Nov. 2026", ASHRAF, ILQAR, "3-1", "Liqa")
                + row(402, "14 Nov. 2026", ILQAR, ASHRAF, "3-0", "Liqa")));
        website.put("/matches/610", details(15, ASHRAF, 13, ILQAR, "18 Nov. 2026", 3, 1, "Liqa"));

        website.put("/matches/results/event/912", history(teamFixture("17 Nov. 2026", teamGame(611, "Əli Məmmədov", "Şəms Quliyeva", "3-0")))
                + "<ul class='pagination'><li><a href='/matches/results/event/912?page=2'>2</a></li></ul>");
        website.put("/matches/results/event/912?page=2", history(teamFixture("13 Nov. 2026",
                teamGame(612, ASHRAF, ILQAR, "3-2") + teamGame(613, ASHRAF, ILQAR, "3-0")
                        + teamGame(614, "Əli Məmmədov", "Şəms Quliyeva", "2-3")
                        + teamGame(615, pair(ERHAN, ELCHIN), pair(ALIKHAN, KHAYAL), "3-0"))));
        website.put("/matches/611", details(17, "Əli Məmmədov", 18, "Şəms Quliyeva", "17 Nov. 2026", 3, 0, "Komanda Liqası"));
        website.put("/matches/612", details(13, ILQAR, 15, ASHRAF, "12 Nov. 2026", 2, 3, "Komanda Liqası"));
        website.put("/matches/613", details(115, ASHRAF, 13, ILQAR, "11 Nov. 2026", 3, 0, "Komanda Liqası"));
        website.put("/matches/614", details(17, "Əli Məmmədov", 18, "Şəms Quliyeva", "13 Nov. 2026", 2, 3, "Komanda Liqası"));
        website.put("/matches/615", details(player(14, ERHAN) + player(22, ELCHIN),
                player(7, ALIKHAN) + player(70, KHAYAL), "10 Nov. 2026", 3, 0, "Komanda Liqası"));
    }

    static String laterPage() {
        return history(overlappingDuelRow());
    }

    private static String overlappingDuelRow() { return row(502, "16 Nov. 2026", ASHRAF, ILQAR, "1-3", "Duello"); }

    static void publishDoubles(Map<String, String> website) {
        website.putIfAbsent("/matches/results", EMPTY_TOURNAMENT_ARCHIVE);
        website.putIfAbsent("/matches/duellos", history(""));
        publishPlayer(website, "erh", 14, ERHAN);
        publishPlayer(website, "el%C3%A7", 22, ELCHIN);
        publishPlayer(website, "%C9%99li", 7, ALIKHAN);
        publishPlayer(website, "x%C9%99y", 70, KHAYAL);
        String event = "Ping Pong Club Qoşa Reytinq Turniri";
        String published = row(30583, "09 Nov. 2026", pair(ERHAN, ELCHIN), pair(ALIKHAN, KHAYAL), "3-2", event);
        website.put("/matches/results/player/7", history(published)
                + "<ul class='pagination'><li><a href='/matches/results/player/7?page=2'>2</a></li></ul>");
        website.put("/matches/results/player/7?page=2", laterDoublesPage());
        website.put("/matches/results/player/14", history(published));
        website.put("/matches/30583", details(player(14, ERHAN) + player(22, ELCHIN),
                player(70, KHAYAL) + player(7, ALIKHAN), "09 Nov. 2026", 3, 2, event));
        website.put("/matches/31001", details(player(7, ALIKHAN) + player(70, KHAYAL),
                player(22, ELCHIN) + player(14, ERHAN), "20 Nov. 2026", 3, 1, "Liqa"));
    }

    static String laterDoublesPage() {
        return history(row(31001, "20 Nov. 2026", pair(ALIKHAN, KHAYAL), pair(ELCHIN, ERHAN), "3-1", "Liqa"));
    }

    static void publishHistoricalDoubles(Map<String, String> website) {
        website.putIfAbsent("/matches/results", EMPTY_TOURNAMENT_ARCHIVE);
        website.putIfAbsent("/matches/duellos", history(""));
        publishPlayer(website, "x%C9%99y", 70, KHAYAL);
        publishPlayer(website, "fir", 32, FIRDOVSI);
        publishPlayer(website, "vaq", 126, VAQIF);
        publishPlayer(website, "%C5%9Fak", 76, SHAKIR);
        String event = "BTTC Qoşa Reytinq Turniri #105";
        website.put("/matches/results/player/76", history(
                row(23924, "25 Mar. 2026", pair(KHAYAL, FIRDOVSI), pair(SHAKIR, VAQIF), "3-1", event)
                + row(24001, "26 Mar. 2026", pair(KHAYAL, VAQIF), pair(SHAKIR, FIRDOVSI), "3-2", event)
                + row(24002, "27 Mar. 2026", pair(KHAYAL, ERHAN), pair(SHAKIR, VAQIF), "3-0", event)
                + row(24003, "28 Mar. 2026", pair(KHAYAL, FIRDOVSI), pair(SHAKIR, VAQIF), "3-0", event)));
        website.put("/matches/23924", details(player(70, KHAYAL) + player(32, FIRDOVSI),
                player(126, VAQIF) + player(76, SHAKIR), "25 Mar. 2026", 3, 1, event));
        website.put("/matches/24001", details(player(70, KHAYAL) + player(126, VAQIF),
                player(76, SHAKIR) + player(32, FIRDOVSI), "26 Mar. 2026", 3, 2, event));
        website.put("/matches/24002", details(player(70, KHAYAL) + player(14, ERHAN),
                player(76, SHAKIR) + player(126, VAQIF), "27 Mar. 2026", 3, 0, event));
        website.put("/matches/24003", details(player(70, KHAYAL) + player(132, FIRDOVSI),
                player(76, SHAKIR) + player(126, VAQIF), "28 Mar. 2026", 3, 0, event));
    }

    private static void publishPlayer(Map<String, String> website, String query, long id, String name) {
        website.put("/search?query=" + query, directory(id, name));
        website.put("/matches/results/player/" + id, history(""));
    }

    private static String pair(String first, String second) { return first + "</div><div>" + second; }

    private static String directory(long id, String name) {
        return "<div id='center'><div class='ranking-page-list'><a href='/players/" + id
                + "'><div class='rank-name'>" + name + "</div></a></div></div>";
    }

    private static String history(String rows) { return "<div id='center'><div class='matches-box'>" + rows + "</div></div>"; }

    private static String duelPagination(int... pages) {
        StringBuilder links = new StringBuilder("<ul class='pagination'>");
        for (int page : pages) {
            links.append("<li><a href='/matches/duellos?page=").append(page).append("'>").append(page).append("</a></li>");
        }
        return links.append("</ul>").toString();
    }

    private static String row(int id, String day, String left, String right, String score, String event) {
        return "<div><div class='divs-header'>" + day + "</div><div><a href='/matches/" + id + "'><div class='match-box'>"
                + "<div class='col-3'><div>" + left + "</div></div><div class='col-2'><div class='center'>" + score
                + "</div></div><div class='col-3'><div>" + right + "</div></div><div class='col-4'>" + event + "</div></div></a></div></div>";
    }

    private static String teamFixture(String day, String games) {
        return "<div><div class='divs-header'>" + day + "</div><div class='team-match-box'>"
                + "<div class='team-match-main'><div class='col-3'>Klub A</div><div class='col-3'>Klub B</div>"
                + "<div class='col-4'>Klub turniri</div></div>"
                + "<div class='child-matches-group' style='display:none'>" + games + "</div></div></div>";
    }

    private static String teamGame(int id, String left, String right, String score) {
        return "<div class='team-matches-matches-box'><a href='/matches/" + id + "'><div class='team-matches-match'>"
                + "<div class='col-5'><div>" + left + "</div></div><div class='col-2'><div class='center'>" + score
                + "</div></div><div class='col-5'><div>" + right + "</div></div></div></a></div>";
    }

    private static String details(long leftId, String left, long rightId, String right, String day, int leftScore, int rightScore, String event) {
        return details(player(leftId, left), player(rightId, right), day, leftScore, rightScore, event);
    }

    private static String player(long id, String name) {
        return "<a href='/players/" + id + "'><div class='match-player-name'>" + name + "</div></a>";
    }

    private static String details(String left, String right, String day, int leftScore, int rightScore, String event) {
        return "<div id='center'><div class='match-players-box'><div class='match-player-box'>" + left
                + "</div><div class='match-date'>" + day
                + "</div><div class='match-tournament'>" + event + "</div><div class='match-player-score'>" + leftScore
                + "</div><div class='match-player-score'>" + rightScore + "</div><div class='match-player-box'>"
                + right + "</div></div></div>";
    }
}
