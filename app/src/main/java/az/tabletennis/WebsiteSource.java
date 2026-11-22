package az.tabletennis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class WebsiteSource {
    static final ResultsFeed.Page FIRST_DUELS_PAGE = new ResultsFeed.Page("/matches/duellos", 0, "Duello", true);
    private final WebsiteClient client;
    private final HttpUrl website;

    WebsiteSource(HttpUrl website) { this(new WebsiteClient(website)); }

    WebsiteSource(WebsiteClient client) {
        this.client = client;
        website = client.websiteUrl();
    }

    EventCatalog events() throws IOException {
        Document document = Jsoup.parse(client.get("/matches/results"));
        Element list = document.selectFirst("#center .tournament-list");
        if (list == null) throw new IOException("Missing event catalogue");
        java.util.Map<Long, EventCatalog.Event> events = new java.util.LinkedHashMap<>();
        for (Element link : list.select("a[href]")) {
            var identity = Pattern.compile("/matches/results/event/(\\d+)/?").matcher(link.attr("href"));
            Element name = link.selectFirst(".tournament-title");
            if (!identity.matches() || name == null || name.text().trim().isEmpty()) throw new IOException("Unusable event");
            long id = sourceId(identity.group(1));
            events.put(id, new EventCatalog.Event(id, name.text()));
        }
        return new EventCatalog(new ArrayList<>(events.values()));
    }

    ResultsFeed feed(ResultsFeed previous, boolean more, EventCatalog catalog, ResultsSelection selection) throws IOException {
        List<ResultsFeed.Page> loaded = previous == null ? new ArrayList<>() : new ArrayList<>(previous.loaded);
        List<ResultsFeed.Page> pending = previous == null ? new ArrayList<>() : new ArrayList<>(previous.pendingEvents);
        for (EventCatalog.Event event : catalog.events) {
            if (!selection.type.events || (selection.eventId != 0 && selection.eventId != event.id)) continue;
            ResultsFeed.Page first = eventResultsPage(event);
            if (!containsPage(loaded, first.path) && !containsPage(pending, first.path)) pending.add(first);
        }
        ResultsFeed.Page duels = previous == null
                ? (selection.type.duels ? FIRST_DUELS_PAGE : null) : previous.nextDuels;
        java.util.Map<Long, MatchSummary> matches = new java.util.LinkedHashMap<>();
        if (previous != null && more) for (MatchSummary match : previous.matches) matches.put(match.id, match);
        if (previous != null && !more) {
            // Refresh only source pages the reader has already loaded; preserve browsing progress.
            duels = null;
            for (ResultsFeed.Page page : loaded) {
                ResultPage result = resultPage(page);
                mergeResults(matches, result.matches);
                if (page.duel) {
                    if (result.next != null && !containsPage(loaded, result.next.path)) duels = result.next;
                } else addContinuation(pending, loaded, result.next);
            }
        } else {
            // Each incremental batch visits at most one duel page and two event-result pages.
            if (duels != null) {
                ResultPage result = resultPage(duels);
                mergeResults(matches, result.matches);
                loaded.add(duels);
                duels = result.next;
            }
            int eventPageLimit = selection.eventId == 0 ? 2 : 1;
            for (int i = 0; i < eventPageLimit && !pending.isEmpty(); i++) {
                ResultsFeed.Page page = pending.remove(0);
                ResultPage result = resultPage(page);
                mergeResults(matches, result.matches);
                loaded.add(page);
                addContinuation(pending, loaded, result.next);
            }
        }
        List<MatchSummary> rows = new ArrayList<>(matches.values());
        sortResults(rows);
        return new ResultsFeed(rows, catalog, loaded, pending, duels);
    }

    private boolean containsPage(List<ResultsFeed.Page> pages, String path) {
        for (ResultsFeed.Page page : pages) if (page.path.equals(path)) return true;
        return false;
    }

    private void addContinuation(List<ResultsFeed.Page> pending, List<ResultsFeed.Page> loaded, ResultsFeed.Page next) {
        if (next != null && !containsPage(loaded, next.path) && !containsPage(pending, next.path)) {
            // Alternate a previously unseen event with an older event's continuation.
            pending.add(Math.min(1, pending.size()), next);
        }
    }

    ResultPage resultPage(ResultsFeed.Page page) throws IOException {
        Document document = Jsoup.parse(client.get(page.path));
        List<MatchSummary> matches = parseResults(document, page.eventId, page.eventName, page.duel);
        HttpUrl current = website.resolve(page.path);
        String currentPage = current.queryParameter("page");
        int number = currentPage == null ? 1 : Integer.parseInt(currentPage);
        int nextNumber = Integer.MAX_VALUE;
        for (Element link : document.select(".pagination li:not(.disabled) a[href]")) {
            HttpUrl target = website.resolve(link.attr("href"));
            if (target == null || !target.encodedPath().equals(current.encodedPath())) continue;
            String value = target.queryParameter("page");
            if (value != null && value.matches("[0-9]{1,8}")) {
                int candidate = Integer.parseInt(value);
                if (candidate > number) nextNumber = Math.min(nextNumber, candidate);
            }
        }
        ResultsFeed.Page next = nextNumber == Integer.MAX_VALUE ? null : new ResultsFeed.Page(
                current.encodedPath() + "?page=" + nextNumber, page.eventId, page.eventName, page.duel);
        return new ResultPage(matches, next);
    }

    private void mergeResults(java.util.Map<Long, MatchSummary> matches, List<MatchSummary> incoming) throws IOException {
        for (MatchSummary match : incoming) {
            MatchSummary old = matches.get(match.id);
            if (old == null) matches.put(match.id, match);
            else {
                if (!old.date.equals(match.date) || !old.score.equals(match.score)
                        || !old.left.equals(match.left) || !old.right.equals(match.right)) throw new IOException("Conflicting match identity");
                // History pages can repeat a match without its tournament metadata.
                boolean preserveHistoryStage = match.eventId == 0 && match.stage == null && old.stage != null;
                MatchSummary event = old.eventId != 0 || preserveHistoryStage ? old : match;
                TournamentStage stage = event.stage;
                if (stage == null && old.eventId == match.eventId) stage = match.stage;
                matches.put(match.id, new MatchSummary(match.id, match.left, match.right, match.date, match.score,
                        event.eventId, event.eventName, old.duel || match.duel, false, stage));
            }
        }
    }

    static ResultsFeed.Page playerHistoryPage(String path) {
        return new ResultsFeed.Page(path, 0, "", false);
    }

    static ResultsFeed.Page eventResultsPage(EventCatalog.Event event) {
        return new ResultsFeed.Page("/matches/results/event/" + event.id, event.id, event.name, false);
    }

    static final class ResultPage {
        final List<MatchSummary> matches;
        final ResultsFeed.Page next;
        ResultPage(List<MatchSummary> matches, ResultsFeed.Page next) { this.matches = matches; this.next = next; }
    }

    private void sortResults(List<MatchSummary> matches) throws IOException {
        java.util.Map<String, java.time.LocalDate> dates = new java.util.HashMap<>();
        try {
            for (MatchSummary match : matches) dates.computeIfAbsent(match.date, MatchSummary::sourceDay);
        } catch (java.time.format.DateTimeParseException failure) {
            throw new IOException("Unusable source match date", failure);
        }
        // Stable ordering within a source day makes no claim about exact match times.
        matches.sort(java.util.Comparator.comparing((MatchSummary match) -> dates.get(match.date)).reversed());
    }

    RatingHistory ratingHistory(long id) throws IOException {
        try {
            org.json.JSONArray data = new org.json.JSONArray(client.get("/api/getPlayerPoints?id=" + id));
            List<RatingHistory.Observation> points = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject row = data.getJSONObject(i);
                String day = row.getString("day");
                if (day.trim().isEmpty()) throw new IOException("Missing rating observation date");
                java.math.BigDecimal value = new java.math.BigDecimal(row.get("point").toString())
                        .setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
                if (!Double.isFinite(value.doubleValue())) throw new IOException("Invalid rating observation");
                points.add(new RatingHistory.Observation(day, value.toPlainString()));
            }
            return new RatingHistory(points);
        } catch (org.json.JSONException | NumberFormatException failure) {
            throw new IOException("Unusable rating history", failure);
        }
    }

    ProfileLoad profile(long id, PlayerProfile previous, boolean more) throws IOException {
        String name = previous == null ? "" : previous.name;
        Integer points = previous == null ? null : previous.points;
        String photoUrl = previous == null ? null : previous.photoUrl;
        Integer amateurPosition = previous == null ? null : previous.amateurPosition;
        if (!more || previous == null) {
            Document document = Jsoup.parse(client.get("/players/" + id));
            Element sourceName = document.selectFirst("#center .player-block .name-div");
            Element identity = document.selectFirst("#chart-user-id");
            if (sourceName == null || sourceName.text().trim().isEmpty() || identity == null || !identity.text().equals(Long.toString(id))) {
                throw new IOException("Unusable player identity");
            }
            name = sourceName.text();
            Element photo = document.selectFirst("#center .player-block .player-block-picture img[src]");
            photoUrl = photo == null ? null : client.photoUrl(photo.attr("src"));
            points = null;
            amateurPosition = null;
            RankingsSource rankings = new RankingsSource(website);
            List<RankingSnapshot.Player> amateurs = rankings.fetchAmateurs();
            int position = 0;
            Integer previousPoints = null;
            for (int i = 0; i < amateurs.size(); i++) {
                RankingSnapshot.Player player = amateurs.get(i);
                if (previousPoints == null || previousPoints != player.points) position = i + 1;
                previousPoints = player.points;
                if (player.id == id) {
                    points = player.points;
                    amateurPosition = position;
                    if (photoUrl == null) photoUrl = player.photoUrl;
                }
            }
            if (points == null) {
                for (RankingSnapshot.Player player : rankings.fetchProfessionals()) if (player.id == id) {
                    points = player.points;
                    if (photoUrl == null) photoUrl = player.photoUrl;
                }
            }
        }
        List<String> loaded = previous == null ? new ArrayList<>() : new ArrayList<>(previous.historyPages);
        List<String> requested = new ArrayList<>();
        if (previous == null) requested.add("/matches/results/player/" + id);
        else if (more) {
            if (previous.nextHistoryPage == null) return new ProfileLoad(previous, new ArrayList<>());
            requested.add(previous.nextHistoryPage);
        } else requested.addAll(loaded);
        java.util.Map<Long, MatchSummary> history = new java.util.LinkedHashMap<>();
        if (previous != null && more) for (MatchSummary match : previous.history) history.put(match.id, match);
        List<MatchDetails> verified = new ArrayList<>();
        String next = previous == null ? null : previous.nextHistoryPage;
        for (String path : requested) {
            ResultPage page = resultPage(playerHistoryPage(path));
            List<MatchSummary> owned = new ArrayList<>();
            for (MatchSummary summary : page.matches) {
                if (!summary.teamChild) owned.add(summary);
                else if (summary.left.contains(name) || summary.right.contains(name)) {
                    // Team fixtures publish other teammates' games too. A name is only a prefilter;
                    // source participant IDs establish ownership even when two names are identical.
                    MatchDetails details = match(summary.id);
                    if (containsPlayer(details.left, id) || containsPlayer(details.right, id)) {
                        verified.add(details);
                        String eventName = details.eventName.isEmpty() ? summary.eventName : details.eventName;
                        owned.add(new MatchSummary(summary.id, playerNames(details.left), playerNames(details.right),
                                details.date, details.score, summary.eventId, eventName, summary.duel, false, summary.stage));
                    }
                }
            }
            mergeResults(history, owned);
            if (!loaded.contains(path)) loaded.add(path);
            next = page.next == null ? null : page.next.path;
        }
        List<MatchSummary> rows = new ArrayList<>(history.values());
        sortResults(rows);
        return new ProfileLoad(new PlayerProfile(id, name, points, rows, loaded, next, photoUrl, amateurPosition), verified);
    }

    PairHistory pairHistory(DoublesPair pair, PairHistory previous, boolean more) throws IOException {
        List<String> loaded = previous == null ? new ArrayList<>() : new ArrayList<>(previous.pages);
        List<String> requested = new ArrayList<>();
        if (previous == null) requested.add("/matches/results/duo/" + pair.id);
        else if (more) {
            if (previous.nextPage == null) return previous;
            requested.add(previous.nextPage);
        } else requested.addAll(loaded);
        java.util.Map<Long, MatchSummary> matches = new java.util.LinkedHashMap<>();
        if (previous != null && more) mergeResults(matches, previous.matches);
        String next = null;
        for (String path : requested) {
            ResultPage page = resultPage(new ResultsFeed.Page(path, 0, "", false));
            for (MatchSummary match : page.matches) {
                if (match.left.size() != 2 || match.right.size() != 2
                        || (!pairSide(match.left, pair) && !pairSide(match.right, pair))) {
                    throw new IOException("Result does not describe this partnership");
                }
            }
            mergeResults(matches, page.matches);
            if (!loaded.contains(path)) loaded.add(path);
            next = page.next == null ? null : page.next.path;
        }
        List<MatchSummary> rows = new ArrayList<>(matches.values());
        sortResults(rows);
        return new PairHistory(pair, rows, loaded, next);
    }

    private boolean pairSide(List<String> names, DoublesPair pair) {
        return names.size() == 2 && ((names.get(0).equals(pair.first.name) && names.get(1).equals(pair.second.name))
                || (names.get(1).equals(pair.first.name) && names.get(0).equals(pair.second.name)));
    }

    private boolean containsPlayer(List<MatchDetails.PlayerLink> players, long id) {
        for (MatchDetails.PlayerLink player : players) if (player.id == id) return true;
        return false;
    }

    private List<String> playerNames(List<MatchDetails.PlayerLink> players) {
        List<String> names = new ArrayList<>();
        for (MatchDetails.PlayerLink player : players) names.add(player.name);
        return names;
    }

    static final class ProfileLoad {
        final PlayerProfile profile;
        final List<MatchDetails> verifiedMatches;
        ProfileLoad(PlayerProfile profile, List<MatchDetails> verifiedMatches) { this.profile = profile; this.verifiedMatches = verifiedMatches; }
    }

    MatchDetails match(long id) throws IOException {
        Document document = Jsoup.parse(client.get("/matches/" + id));
        Element box = document.selectFirst("#center .match-players-box");
        if (box == null) throw new IOException("Missing match details");
        List<Element> sides = box.select(".match-player-box");
        List<Element> scores = box.select(".match-player-score");
        Element date = box.selectFirst(".match-date");
        Element time = box.selectFirst(".match-hour");
        Element event = box.selectFirst(".match-tournament");
        if (sides.size() != 2 || scores.size() != 2 || date == null || date.text().trim().isEmpty()
                || !scores.get(0).text().matches("\\d+") || !scores.get(1).text().matches("\\d+")) {
            throw new IOException("Unusable match details");
        }
        List<MatchDetails.PlayerLink> left = participants(sides.get(0));
        List<MatchDetails.PlayerLink> right = participants(sides.get(1));
        if (left.size() != right.size()) throw new IOException("Unbalanced match sides");
        return new MatchDetails(id, left, right, date.text(), time == null ? "" : time.text(),
                scores.get(0).text() + "-" + scores.get(1).text(), event == null ? "" : event.text());
    }

    private List<MatchDetails.PlayerLink> participants(Element side) throws IOException {
        List<MatchDetails.PlayerLink> players = new ArrayList<>();
        for (Element name : side.select(".match-player-name")) {
            Element link = name.closest("a[href]");
            if (link == null) throw new IOException("Missing participant profile link");
            var identity = Pattern.compile("/players/(\\d+)/?").matcher(link.attr("href"));
            if (!identity.matches() || name == null || name.text().trim().isEmpty()) throw new IOException("Unusable match participant");
            String photoUrl = null;
            // Match images in doubles can be unlinked and ordered differently from the names.
            // Only attribute metadata with an explicit profile link, or an unambiguous singles side.
            for (Element candidate : side.select("a[href]")) {
                if (!candidate.attr("href").equals(link.attr("href"))) continue;
                Element image = candidate.selectFirst("img[src]");
                if (image != null) photoUrl = client.photoUrl(image.attr("src"));
                if (photoUrl != null) break;
            }
            if (photoUrl == null && side.select(".match-player-name").size() == 1) {
                Element image = side.selectFirst(".match-player-image-div img[src]");
                if (image != null) photoUrl = client.photoUrl(image.attr("src"));
            }
            players.add(new MatchDetails.PlayerLink(sourceId(identity.group(1)), name.text(), photoUrl));
        }
        if (players.isEmpty() || players.size() > 2) throw new IOException("Missing match participants");
        return players;
    }

    private List<MatchSummary> parseResults(Document document, long eventId, String eventName, boolean duel) throws IOException {
        List<Element> boxes = document.select("#center .matches-box");
        if (boxes.isEmpty()) throw new IOException("Missing source results");
        List<MatchSummary> results = new ArrayList<>();
        for (Element box : boxes) {
            int before = results.size();
            for (Element day : box.children()) {
                Element date = day.selectFirst(".divs-header");
                for (Element match : day.select("a[href] > .match-box, a[href] > .team-matches-match")) {
                    var identity = Pattern.compile("/matches/(\\d+)/?").matcher(match.parent().attr("href"));
                    boolean teamChild = match.hasClass("team-matches-match");
                    List<Element> sides = match.select(teamChild ? ".col-5" : ".col-3");
                    Element score = match.selectFirst(".col-2 .center");
                    Element event = match.selectFirst(".col-4");
                    Element team = match.closest(".team-match-box");
                    if (event == null && team != null) event = team.selectFirst(".team-match-main > .col-4");
                    if (!identity.matches() || date == null || date.text().trim().isEmpty() || sides.size() != 2
                            || score == null || !score.text().matches("\\d+\\s*-\\s*\\d+")) throw new IOException("Unusable result row");
                    List<String> left = names(sides.get(0));
                    List<String> right = names(sides.get(1));
                    if (left.size() != right.size()) throw new IOException("Unbalanced result sides");
                    String name = eventName.isEmpty() && event != null ? event.text() : eventName;
                    Element stage = null;
                    // Player and doubles histories publish the tournament name without its ID.
                    if (!duel && (eventId != 0 || (!name.isEmpty() && !MatchSummary.isDuelEventName(name)))) {
                        stage = match.selectFirst(".match-box-stage");
                        if (stage == null && team != null) stage = team.selectFirst(".team-match-main > .match-box-stage");
                    }
                    results.add(new MatchSummary(sourceId(identity.group(1)), left, right,
                            date.text(), score.text().replaceAll("\\s", ""), eventId, name, duel, teamChild,
                            stage == null ? null : TournamentStage.fromSource(stage.text())));
                }
            }
            int count = box.select(".match-box, .team-matches-match").size();
            if (results.size() - before != count || (count == 0 && !box.text().trim().isEmpty()
                    && box.selectFirst(".no-info, .team-match-box") == null)) throw new IOException("Incomplete source result list");
        }
        return results;
    }

    private long sourceId(String value) throws IOException {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException("Nonpositive source identity");
            return id;
        } catch (NumberFormatException invalid) {
            throw new IOException("Unusable source identity", invalid);
        }
    }

    private List<String> names(Element side) throws IOException {
        List<String> names = new ArrayList<>();
        for (Element name : side.children()) if (!name.text().trim().isEmpty()) names.add(name.text());
        if (names.isEmpty() || names.size() > 2) throw new IOException("Unusable match participants");
        return names;
    }
}
