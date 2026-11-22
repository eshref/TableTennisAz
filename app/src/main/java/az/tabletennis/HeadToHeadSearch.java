package az.tabletennis;

import java.io.IOException;
import java.io.Serializable;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

final class HeadToHeadSearch {
    private static final Locale AZERBAIJANI = Locale.forLanguageTag("az");
    private static final Pattern PLAYER_PATH = Pattern.compile("/players/([1-9][0-9]*)/?");
    private final WebsiteClient client;
    private final WebsiteSource source;

    HeadToHeadSearch(WebsiteClient client) {
        this.client = client;
        source = new WebsiteSource(client);
    }

    record Player(long id, String name) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    List<Player> findPlayers(String query) throws IOException {
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

        Map<Long, Player> players = new LinkedHashMap<>();
        for (Element link : list.children()) {
            var identity = PLAYER_PATH.matcher(link.attr("href"));
            Element name = link.selectFirst(".rank-name");
            if (!identity.matches() || name == null || name.text().isEmpty()) throw new IOException("Unusable player suggestion");
            try {
                long id = Long.parseLong(identity.group(1));
                players.putIfAbsent(id, new Player(id, name.text()));
            } catch (NumberFormatException failure) {
                throw new IOException("Unusable player identity", failure);
            }
        }
        return new ArrayList<>(players.values());
    }

    void findSingles(Player first, Player second, BiConsumer<MatchSummary, MatchDetails> found) throws IOException {
        // Use the same history order when the reader reverses the selected sides.
        List<Player> owners = first.id() < second.id() ? Arrays.asList(first, second) : Arrays.asList(second, first);
        Set<Long> checked = new HashSet<>();
        for (Player owner : owners) {
            String path = "/matches/results/player/" + owner.id();
            while (path != null) {
                checkInterrupted();
                WebsiteSource.ResultPage page = source.playerHistory(path);
                for (MatchSummary row : page.matches) {
                    if (!namesMatch(row, first, second) || !checked.add(row.id)) continue;
                    checkInterrupted();
                    MatchDetails details = source.match(row.id);
                    if (!playersMatch(details, first, second)) continue;

                    try {
                        MatchSummary.sourceDay(details.date);
                    } catch (DateTimeParseException failure) {
                        throw new IOException("Unusable source match date", failure);
                    }
                    String event = details.eventName.isEmpty() ? row.eventName : details.eventName;
                    found.accept(new MatchSummary(row.id, Collections.singletonList(details.left.get(0).name),
                            Collections.singletonList(details.right.get(0).name), details.date, details.score,
                            row.eventId, event, row.duel, row.teamChild, row.stage), details);
                }
                path = page.next == null ? null : page.next.path;
            }
        }
    }

    private boolean namesMatch(MatchSummary row, Player first, Player second) {
        if (row.left.size() != 1 || row.right.size() != 1) return false;
        String left = row.left.get(0);
        String right = row.right.get(0);
        return (left.equalsIgnoreCase(first.name()) && right.equalsIgnoreCase(second.name()))
                || (left.equalsIgnoreCase(second.name()) && right.equalsIgnoreCase(first.name()));
    }

    private boolean playersMatch(MatchDetails details, Player first, Player second) {
        if (details.left.size() != 1 || details.right.size() != 1) return false;
        long left = details.left.get(0).id;
        long right = details.right.get(0).id;
        return (left == first.id() && right == second.id()) || (left == second.id() && right == first.id());
    }

    private void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Player history search interrupted");
    }
}
