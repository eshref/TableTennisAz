package az.tabletennis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

final class RankingsSource {
    private static final Pattern PLAYER_PATH = Pattern.compile("/players/(\\d+)/?");
    private static final Pattern POINTS = Pattern.compile("(\\d+)\\s+Xal", Pattern.CASE_INSENSITIVE);
    private final HttpUrl website;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    RankingsSource(HttpUrl website) { this.website = website; }

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
        HttpUrl url = website.newBuilder().addPathSegment("players").addQueryParameter("level", category).build();
        Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) TableTennisAz/0.1")
                .header("Accept", "text/html")
                .header("Accept-Language", "az-AZ,az;q=0.9")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Rankings HTTP " + response.code());
            }
            Elements rows = Jsoup.parse(response.body().string()).select(".ranking-page-list > a");
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
}
