package az.tabletennis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

final class RankingsSource {
    private static final Pattern PLAYER_PATH = Pattern.compile("/players/(\\d+)/?");
    private static final Pattern POINTS = Pattern.compile("(\\d+)\\s+Xal", Pattern.CASE_INSENSITIVE);
    private final WebsiteClient client;

    RankingsSource(HttpUrl website) { client = new WebsiteClient(website); }

    List<RankingSnapshot.Player> fetch() throws IOException {
        Map<Long, RankingSnapshot.Player> combined = new LinkedHashMap<>();
        for (String category : new String[] {"amateur", "pro"}) {
            for (RankingSnapshot.Player player : fetchCategory(category)) {
                RankingSnapshot.Player previous = combined.putIfAbsent(player.id, player);
                if (previous != null && (previous.points != player.points || !previous.name.equals(player.name))) {
                    throw new IOException("Conflicting source player identity");
                }
            }
        }
        List<RankingSnapshot.Player> players = new ArrayList<>(combined.values());
        players.sort(Comparator.comparingInt((RankingSnapshot.Player p) -> p.points).reversed()
                .thenComparingLong(p -> p.id));
        return players;
    }

    private List<RankingSnapshot.Player> fetchCategory(String category) throws IOException {
        Elements rows = Jsoup.parse(client.get("/players?level=" + category)).select(".ranking-page-list > a");
        if (rows.isEmpty()) throw new IOException("Missing source rankings");
        List<RankingSnapshot.Player> players = new ArrayList<>();
        for (Element row : rows) {
            Matcher identity = PLAYER_PATH.matcher(row.attr("href"));
            Element name = row.selectFirst(".rank-name");
            Element points = row.selectFirst(".rank-points");
            Matcher numericPoints = POINTS.matcher(points == null ? "" : points.text());
            if (!identity.matches() || name == null || name.text().trim().isEmpty() || !numericPoints.matches()) {
                throw new IOException("Unusable source ranking row");
            }
            try {
                players.add(new RankingSnapshot.Player(Long.parseLong(identity.group(1)),
                        name.text(), Integer.parseInt(numericPoints.group(1))));
            } catch (NumberFormatException invalidNumber) {
                throw new IOException("Invalid source ranking number", invalidNumber);
            }
        }
        return players;
    }
}
