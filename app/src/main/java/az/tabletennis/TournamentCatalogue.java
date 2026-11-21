package az.tabletennis;

import java.io.IOException;
import java.io.Serializable;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

final class TournamentCatalogue implements Serializable {
    enum Format { SINGLES, DOUBLES }

    enum Medal {
        GOLD("gold", 1), SILVER("silver", 2), BRONZE("bronze", 3);

        final String key;
        final int place;

        Medal(String key, int place) {
            this.key = key;
            this.place = place;
        }
    }

    static final String STORE_KEY = "tournaments";
    private static final long serialVersionUID = 1L;
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern PRIZE = Pattern.compile("[0-9]+(?:[.,][0-9]+)?\\s+AZN");

    private final List<Tournament> singles;
    private final List<Tournament> doubles;
    private final Map<Long, Long> firstListed;
    private final long baseline;

    private TournamentCatalogue(List<Tournament> singles, List<Tournament> doubles,
                                Map<Long, Long> firstListed, long baseline) {
        this.singles = singles;
        this.doubles = doubles;
        this.firstListed = firstListed;
        this.baseline = baseline;
    }

    static TournamentCatalogue parse(String body) throws IOException {
        try {
            JSONTokener input = new JSONTokener(body);
            Object value = input.nextValue();
            if (!(value instanceof JSONObject catalogue) || input.nextClean() != 0
                    || integer(catalogue, "version") != 1) throw new IOException("Unsupported tournament catalogue");
            Map<Long, Long> firstListed = catalogue.has("firstListed")
                    ? firstObservations(catalogue.getJSONObject("firstListed")) : null;
            JSONArray rows = catalogue.getJSONArray("tournaments");
            List<Tournament> singles = new ArrayList<>();
            List<Tournament> doubles = new ArrayList<>();
            Set<Long> identities = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                long id = integer(row, "id");
                if (id <= 0 || !identities.add(id)) throw new IOException("Invalid tournament identity");
                if (firstListed != null && !firstListed.containsKey(id))
                    throw new IOException("Missing first observation for tournament " + id);
                switch (text(row, "format")) {
                    case "singles" -> singles.add(tournament(row, id, Format.SINGLES));
                    case "doubles" -> doubles.add(tournament(row, id, Format.DOUBLES));
                    case "team", "unknown" -> { }
                    default -> throw new IOException("Invalid tournament format");
                }
            }
            Comparator<Tournament> newestFirst = Comparator.comparing((Tournament tournament) -> tournament.end).reversed();
            singles.sort(newestFirst);
            doubles.sort(newestFirst);
            return new TournamentCatalogue(singles, doubles, firstListed, 0);
        } catch (JSONException | DateTimeParseException failure) {
            throw new IOException("Invalid tournament catalogue", failure);
        }
    }

    TournamentCatalogue after(TournamentCatalogue previous) throws IOException {
        if (previous != null && previous.firstListed != null
                && (firstListed == null || !firstListed.entrySet().containsAll(previous.firstListed.entrySet())))
            throw new IOException("Tournament catalogue lost Results index history");
        if (firstListed == null) return this;
        long cutoff = previous != null && previous.baseline > 0
                ? previous.baseline : Collections.max(firstListed.values());
        return new TournamentCatalogue(singles, doubles, firstListed, cutoff);
    }

    Set<String> newIds(Format format) {
        Set<String> ids = new HashSet<>();
        if (baseline > 0) {
            for (Tournament tournament : entries(format)) {
                if (firstListed.get(tournament.id) > baseline) ids.add(Long.toString(tournament.id));
            }
        }
        return ids;
    }

    List<Tournament> find(Format format, String query) {
        String term = searchable(query.trim());
        List<Tournament> matches = new ArrayList<>();
        for (Tournament tournament : entries(format)) {
            if (tournament.searchName.contains(term)) matches.add(tournament);
        }
        return matches;
    }

    private List<Tournament> entries(Format format) {
        List<Tournament> entries = format == Format.DOUBLES ? doubles : singles;
        return entries == null ? Collections.emptyList() : entries; // A snapshot saved before doubles.
    }

    private static Map<Long, Long> firstObservations(JSONObject observations) throws JSONException, IOException {
        Map<Long, Long> firstListed = new HashMap<>();
        Iterator<String> keys = observations.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            long id;
            try {
                id = Long.parseLong(key);
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid Results index identity", invalid);
            }
            long observedAt = integer(observations, key);
            if (id <= 0 || !key.equals(Long.toString(id)) || observedAt <= 0)
                throw new IOException("Invalid Results index observation");
            firstListed.put(id, observedAt);
        }
        if (firstListed.isEmpty()) throw new IOException("Missing Results index history");
        return firstListed;
    }

    private static Tournament tournament(JSONObject row, long id, Format format) throws JSONException, IOException {
        String name = text(row, "name");
        LocalDate start = LocalDate.parse(text(row, "start"));
        LocalDate end = LocalDate.parse(text(row, "end"));
        if (end.isBefore(start)) throw new IOException("Invalid tournament date range");
        String countKey = format == Format.DOUBLES ? "pairs" : "players";
        Long count = row.isNull(countKey) ? null : integer(row, countKey);
        if (count != null && count < 0) throw new IOException("Invalid tournament " + countKey + " count");
        String prize = row.isNull("prize") ? null : text(row, "prize");
        if (prize != null && !PRIZE.matcher(prize).matches()) throw new IOException("Invalid tournament prize");
        return new Tournament(id, name, start, end,
                format == Format.SINGLES ? count : null, format == Format.DOUBLES ? count : null, prize,
                podium(row.optJSONObject("podium"), format == Format.DOUBLES ? 2 : 1));
    }

    private static Map<Medal, List<Medalist>> podium(JSONObject podium, int entrantsPerPlace) {
        Map<Medal, List<Medalist>> placements = new EnumMap<>(Medal.class);
        if (podium == null) return placements;
        for (Medal medal : Medal.values()) {
            JSONArray entrants = podium.optJSONArray(medal.key);
            if (entrants == null || entrants.length() != entrantsPerPlace) continue;
            List<Medalist> medalists = new ArrayList<>(entrantsPerPlace);
            Set<Long> identities = new HashSet<>();
            for (int i = 0; i < entrants.length(); i++) {
                JSONObject entrant = entrants.optJSONObject(i);
                if (entrant == null) break;
                try {
                    long id = integer(entrant, "id");
                    if (id <= 0 || !identities.add(id)) break;
                    Object photo = entrant.opt("photo");
                    medalists.add(new Medalist(id, text(entrant, "name"), photo instanceof String path ? path : null));
                } catch (JSONException | IOException invalidEntrant) {
                    break;
                }
            }
            if (medalists.size() == entrantsPerPlace) placements.put(medal, medalists);
        }
        return placements;
    }

    private static String text(JSONObject row, String key) throws JSONException, IOException {
        if (row.get(key) instanceof String value && !value.trim().isEmpty()) return value;
        throw new IOException("Missing tournament field " + key);
    }

    private static long integer(JSONObject row, String key) throws JSONException, IOException {
        Object value = row.get(key);
        if (value instanceof Integer || value instanceof Long) return ((Number) value).longValue();
        throw new IOException("Invalid tournament field " + key);
    }

    private static String searchable(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('ı', 'i').replace('ə', 'e');
        return ACCENTS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }

    static final class Medalist implements Serializable {
        private static final long serialVersionUID = 1L;
        final long id;
        final String name;
        final String photo;

        Medalist(long id, String name, String photo) {
            this.id = id;
            this.name = name;
            this.photo = photo;
        }
    }

    static final class Tournament implements Serializable {
        private static final long serialVersionUID = 1L;
        final long id;
        final String name;
        final LocalDate start;
        final LocalDate end;
        final Long players;
        final Long pairs;
        final String prize;
        private final String searchName;
        private final Map<Medal, List<Medalist>> podium;

        Tournament(long id, String name, LocalDate start, LocalDate end, Long players, Long pairs, String prize,
                   Map<Medal, List<Medalist>> podium) {
            this.id = id;
            this.name = name;
            this.start = start;
            this.end = end;
            this.players = players;
            this.pairs = pairs;
            this.prize = prize;
            this.searchName = searchable(name);
            this.podium = podium;
        }

        Map<Medal, List<Medalist>> podium() {
            return podium == null ? Collections.emptyMap() : podium;
        }
    }
}
