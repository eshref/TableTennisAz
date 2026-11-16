package az.tabletennis;

abstract class DoublesUiScenario extends ProductUiScenario {
    protected void prepareDoubles() {
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>"
                + pairRow(110, 1, 800) + pairRow(220, 2, 800) + pairRow(330, 3, 600) + "</div></div>");
        website.put("/api/getDuoInfo?id=110", pairInfo(110, 12, "İlqar Həsənov", 13, "Əli Məmmədov"));
        website.put("/api/getDuoInfo?id=220", pairInfo(220, 12, "İlqar Həsənov", 11, "Şəms Quliyeva"));
        website.put("/api/getDuoInfo?id=330", pairInfo(330, 14, "Işıl Əliyeva", 13, "Əli Məmmədov"));
    }

    protected static String pairRow(long id, int position, int points) {
        return "<div><div class='rank-duo-box rank-box'><div class='rank-duo-box-main'>"
                + "<div class='rank-position'>#" + position + "</div><div class='rank-name'>İ. H. | Ə. M.</div>"
                + "<div class='rank-wins'>0 Bonus</div><div class='rank-points'>" + points + " Xal</div>"
                + "<div class='hidden duo-id' value='" + id + "'>" + id + "</div></div></div></div>";
    }

    protected String pairInfo(long id, long firstId, String first, long secondId, String second) {
        return "{\"player1Id\":" + firstId + ",\"player1FullName\":\"" + first + "\",\"player2Id\":"
                + secondId + ",\"player2FullName\":\"" + second + "\",\"resultsUrl\":\""
                + server.url("/matches/results/duo/" + id) + "\"}";
    }

    protected String pairResult(long id, String date, String event) {
        return "<div><div class='divs-header'>" + date + "</div><div><a href='/matches/" + id
                + "'><div class='match-box'><div class='col-3'><div>İlqar Həsənov</div><div>Əli Məmmədov</div>"
                + "</div><div class='col-2'><div class='center'>3-2</div></div>"
                + "<div class='col-3'><div>Şəms Quliyeva</div><div>Işıl Əliyeva</div></div>"
                + "<div class='col-4'>" + event + "</div></div></a></div></div>";
    }

    protected String pairHistory(String rows) {
        return "<div id='center'><div class='matches-box'>" + rows + "</div></div>";
    }
}
