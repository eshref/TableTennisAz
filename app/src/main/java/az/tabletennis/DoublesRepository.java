package az.tabletennis;

import android.content.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import okhttp3.HttpUrl;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Website pair identities are cached separately so interrupted first loads can resume. */
final class DoublesRepository {
    private final Context context;
    private final WebsiteClient client;
    private final HttpUrl website;

    DoublesRepository(Context context, HttpUrl website) {
        this.context = context;
        this.website = website;
        client = new WebsiteClient(website);
    }

    DoublesRanking ranking(Progress progress) throws IOException {
        Element list = Jsoup.parse(client.get("/players/duos")).selectFirst("#center .ranking-page-list");
        if (list == null || list.children().isEmpty()) throw new IOException("Missing doubles rankings");
        List<PublishedRow> positions = new ArrayList<>();
        Set<Long> identities = new HashSet<>();
        int previousPosition = 0;
        int previousPoints = Integer.MAX_VALUE;
        for (Element container : list.children()) {
            Element row = container.selectFirst(".rank-duo-box-main");
            if (row == null) throw new IOException("Unusable doubles row");
            long id = number(row, ".duo-id", "[1-9][0-9]*");
            int position = integer(row, ".rank-position", "#[1-9][0-9]*", "#");
            int points = integer(row, ".rank-points", "[0-9]+\\s+Xal", "\\s+Xal");
            if (!identities.add(id) || position <= previousPosition || points > previousPoints) {
                throw new IOException("Conflicting doubles ranking");
            }
            positions.add(new PublishedRow(id, position, points));
            previousPosition = position;
            previousPoints = points;
        }
        List<DoublesRanking.Row> rows = new ArrayList<>();
        progress.update(0, positions.size());
        for (PublishedRow row : positions) {
            rows.add(new DoublesRanking.Row(pair(row.id), row.position, row.points));
            progress.update(rows.size(), positions.size());
        }
        return new DoublesRanking(rows);
    }

    DoublesPair pair(long id) throws IOException {
        SavedDataStore<DoublesPair> store = new SavedDataStore<>(context, "duo-" + id, DoublesPair.class);
        SavedData<DoublesPair> cached = store.read();
        if (cached != null && cached.value.id == id) return cached.value;
        DoublesPair pair = parsePair(id, pairInfo(id));
        store.write(new SavedData<>(pair, System.currentTimeMillis()));
        return pair;
    }

    /** Optional discovery for old identities; callers run this outside the ranking refresh. */
    DoublesPair portraitMetadata(DoublesPair expected) throws IOException {
        DoublesPair cached = pair(expected.id);
        DoublesPair actual = cached.photoMetadataResolved ? cached : parsePair(expected.id, pairInfo(expected.id));
        boolean sameOrder = actual.first.id == expected.first.id && actual.second.id == expected.second.id;
        if (!expected.hasSamePlayers(actual)) throw new IOException("Metadata does not describe this partnership");
        DoublesPair enriched = new DoublesPair(expected.id, expected.first, expected.second,
                sameOrder ? actual.firstPhotoUrl : actual.secondPhotoUrl,
                sameOrder ? actual.secondPhotoUrl : actual.firstPhotoUrl, true);
        if (!cached.photoMetadataResolved) new SavedDataStore<>(context, "duo-" + expected.id, DoublesPair.class)
                .write(new SavedData<>(enriched, System.currentTimeMillis()));
        return enriched;
    }

    PairMedals medals(DoublesPair expected) throws IOException {
        JSONObject data = pairInfo(expected.id);
        DoublesPair actual = parsePair(expected.id, data);
        if (!expected.hasSamePlayers(actual)) {
            throw new IOException("Metadata does not describe this partnership");
        }
        return new PairMedals(medalCount(data, "firstPlaceCount"), medalCount(data, "secondPlaceCount"),
                medalCount(data, "thirdPlaceCount"));
    }

    private JSONObject pairInfo(long id) throws IOException {
        try { return new JSONObject(client.get("/api/getDuoInfo?id=" + id)); }
        catch (JSONException invalid) { throw new IOException("Unusable doubles metadata", invalid); }
    }

    private Integer medalCount(JSONObject data, String key) throws IOException {
        if (data.isNull(key)) return null;
        Object value = data.opt(key);
        if (!(value instanceof Number) || !value.toString().matches("[0-9]+")) {
            throw new IOException("Invalid pair medal count");
        }
        try { return Integer.valueOf(value.toString()); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid pair medal count", invalid); }
    }

    private DoublesPair parsePair(long id, JSONObject data) throws IOException {
        try {
            long first = data.getLong("player1Id");
            long second = data.getLong("player2Id");
            String firstName = data.getString("player1FullName").replaceAll("[\\s\\u00a0]+", " ").trim();
            String secondName = data.getString("player2FullName").replaceAll("[\\s\\u00a0]+", " ").trim();
            HttpUrl results = website.resolve(data.getString("resultsUrl"));
            if (first <= 0 || second <= 0 || first == second || firstName.isEmpty() || secondName.isEmpty()
                    || results == null || !results.encodedPath().equals("/matches/results/duo/" + id)) {
                throw new IOException("Unusable doubles identity");
            }
            return new DoublesPair(id, new MatchDetails.PlayerLink(first, firstName),
                    new MatchDetails.PlayerLink(second, secondName), photo(data, "player1PictureUrl"),
                    photo(data, "player2PictureUrl"), true);
        } catch (JSONException invalid) {
            throw new IOException("Unusable doubles identity", invalid);
        }
    }

    private String photo(JSONObject data, String key) {
        Object value = data.opt(key);
        return value instanceof String ? client.photoUrl((String) value) : null;
    }

    interface Progress { void update(int completed, int total); }

    private static final class PublishedRow {
        final long id;
        final int position;
        final int points;

        PublishedRow(long id, int position, int points) {
            this.id = id;
            this.position = position;
            this.points = points;
        }
    }

    private long number(Element row, String selector, String format) throws IOException {
        Element field = row.selectFirst(selector);
        if (field == null || !field.text().matches(format)) throw new IOException("Invalid doubles number");
        try { return Long.parseLong(field.text()); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid doubles number", invalid); }
    }

    private int integer(Element row, String selector, String format, String suffix) throws IOException {
        Element field = row.selectFirst(selector);
        if (field == null || !field.text().matches(format)) throw new IOException("Invalid doubles number");
        try { return Integer.parseInt(field.text().replaceAll(suffix, "")); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid doubles number", invalid); }
    }
}
