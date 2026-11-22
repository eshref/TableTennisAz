package az.tabletennis;

import java.io.IOException;
import java.io.Serializable;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
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

    void findMeetings(List<Player> firstSide, List<Player> secondSide,
                      BiConsumer<MatchSummary, MatchDetails> found) throws IOException {
        // Read every selected player's history in the same order for equivalent selections.
        List<Player> owners = new ArrayList<>(firstSide);
        owners.addAll(secondSide);
        owners.sort(Comparator.comparingLong(Player::id));
        Set<Long> checked = new HashSet<>();
        for (Player owner : owners) {
            String path = "/matches/results/player/" + owner.id();
            while (path != null) {
                checkInterrupted();
                WebsiteSource.ResultPage page = source.playerHistory(path);
                for (MatchSummary row : page.matches) {
                    if (!matchesSides(row.left, row.right, firstSide, secondSide,
                            (name, player) -> name.equalsIgnoreCase(player.name())) || !checked.add(row.id)) continue;
                    checkInterrupted();
                    MatchDetails details = source.match(row.id);
                    if (!matchesSides(details.left, details.right, firstSide, secondSide,
                            (participant, player) -> participant.id == player.id())) continue;

                    try {
                        MatchSummary.sourceDay(details.date);
                    } catch (DateTimeParseException failure) {
                        throw new IOException("Unusable source match date", failure);
                    }
                    String event = details.eventName.isEmpty() ? row.eventName : details.eventName;
                    found.accept(new MatchSummary(row.id, names(details.left), names(details.right), details.date, details.score,
                            row.eventId, event, row.duel, row.teamChild, row.stage), details);
                }
                path = page.next == null ? null : page.next.path;
            }
        }
    }

    private <T> boolean matchesSides(List<T> left, List<T> right, List<Player> firstSide, List<Player> secondSide,
                                     BiPredicate<T, Player> matchesPlayer) {
        return (matchesSide(left, firstSide, matchesPlayer) && matchesSide(right, secondSide, matchesPlayer))
                || (matchesSide(left, secondSide, matchesPlayer) && matchesSide(right, firstSide, matchesPlayer));
    }

    private <T> boolean matchesSide(List<T> published, List<Player> selected, BiPredicate<T, Player> matchesPlayer) {
        if (published.size() != selected.size()) return false;
        if (selected.size() == 1) return matchesPlayer.test(published.get(0), selected.get(0));
        return (matchesPlayer.test(published.get(0), selected.get(0)) && matchesPlayer.test(published.get(1), selected.get(1)))
                || (matchesPlayer.test(published.get(0), selected.get(1)) && matchesPlayer.test(published.get(1), selected.get(0)));
    }

    private List<String> names(List<MatchDetails.PlayerLink> side) {
        List<String> names = new ArrayList<>(side.size());
        for (MatchDetails.PlayerLink player : side) names.add(player.name);
        return names;
    }

    private void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Player history search interrupted");
    }
}
