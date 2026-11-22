package az.tabletennis;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

final class HeadToHeadSearch {
    private static final Locale AZERBAIJANI = Locale.forLanguageTag("az");
    private static final Pattern PLAYER_PATH = Pattern.compile("/players/([1-9][0-9]*)/?");
    private final WebsiteClient client;

    HeadToHeadSearch(WebsiteClient client) {
        this.client = client;
    }

    record Player(long id, String name) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    record Suggestion(Player player, String photoUrl) {}

    enum Winner { FIRST, SECOND, NONE }

    record Meeting(MatchSummary match, Winner winner) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    List<Suggestion> findPlayers(String query) throws IOException {
        // The public directory matches a dotted capital İ only when sent as lowercase i.
        HttpUrl url = client.websiteUrl().newBuilder().encodedPath("/search")
                .addQueryParameter("query", query.toLowerCase(AZERBAIJANI)).build();
        Element center = Jsoup.parse(client.get(url.toString())).selectFirst("#center");
        if (center == null) throw new IOException("Missing player directory");
        Element list = center.selectFirst(".ranking-page-list");
        if (list == null) {
            if (center.selectFirst(".no-info") != null) return Collections.emptyList();
            throw new IOException("Missing player directory");
        }

        Map<Long, Suggestion> suggestions = new LinkedHashMap<>();
        for (Element link : list.children()) {
            var identity = PLAYER_PATH.matcher(link.attr("href"));
            Element name = link.selectFirst(".rank-name");
            if (!identity.matches() || name == null || name.text().isEmpty()) throw new IOException("Unusable player suggestion");
            try {
                long id = Long.parseLong(identity.group(1));
                Element photo = link.selectFirst(".rank-picture img[src]");
                String photoUrl = photo == null ? null : client.photoUrl(photo.attr("src"));
                suggestions.putIfAbsent(id, new Suggestion(new Player(id, name.text()), photoUrl));
            } catch (NumberFormatException failure) {
                throw new IOException("Unusable player identity", failure);
            }
        }
        return new ArrayList<>(suggestions.values());
    }

    static Winner winner(String score, boolean firstSideOnLeft) {
        // The index validates the published score before tallying wins.
        String[] scores = score.split("-", 2);
        int comparison = new BigInteger(scores[0]).compareTo(new BigInteger(scores[1]));
        if (comparison == 0) return Winner.NONE;
        return (comparison > 0) == firstSideOnLeft ? Winner.FIRST : Winner.SECOND;
    }
}
