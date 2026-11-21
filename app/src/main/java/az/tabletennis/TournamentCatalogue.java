package az.tabletennis;

import java.io.IOException;
import java.io.Serializable;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

final class TournamentCatalogue implements Serializable {
    static final String STORE_KEY = "tournaments";
    private static final long serialVersionUID = 1L;
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern PRIZE = Pattern.compile("[0-9]+(?:[.,][0-9]+)?\\s+AZN");

    private final List<Tournament> singles;

    private TournamentCatalogue(List<Tournament> singles) {
        this.singles = singles;
    }

    static TournamentCatalogue parse(String body) throws IOException {
        try {
            JSONTokener input = new JSONTokener(body);
            Object value = input.nextValue();
            if (!(value instanceof JSONObject catalogue) || input.nextClean() != 0
                    || integer(catalogue, "version") != 1) throw new IOException("Unsupported tournament catalogue");
            JSONArray rows = catalogue.getJSONArray("tournaments");
            List<Tournament> singles = new ArrayList<>();
            Set<Long> identities = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                long id = integer(row, "id");
                if (id <= 0 || !identities.add(id)) throw new IOException("Invalid tournament identity");
                switch (text(row, "format")) {
                    case "singles" -> singles.add(singlesTournament(row, id));
                    case "doubles", "team", "unknown" -> { }
                    default -> throw new IOException("Invalid tournament format");
                }
            }
            singles.sort(Comparator.comparing((Tournament tournament) -> tournament.end).reversed());
            return new TournamentCatalogue(singles);
        } catch (JSONException | DateTimeParseException failure) {
            throw new IOException("Invalid tournament catalogue", failure);
        }
    }

    List<Tournament> findSingles(String query) {
        String term = searchable(query.trim());
        List<Tournament> matches = new ArrayList<>();
        for (Tournament tournament : singles) {
            if (tournament.searchName.contains(term)) matches.add(tournament);
        }
        return matches;
    }

    private static Tournament singlesTournament(JSONObject row, long id) throws JSONException, IOException {
        String name = text(row, "name");
        LocalDate start = LocalDate.parse(text(row, "start"));
        LocalDate end = LocalDate.parse(text(row, "end"));
        if (end.isBefore(start)) throw new IOException("Invalid tournament date range");
        Long count = row.isNull("players") ? null : integer(row, "players");
        if (count != null && count < 0) throw new IOException("Invalid tournament player count");
        String prize = row.isNull("prize") ? null : text(row, "prize");
        if (prize != null && !PRIZE.matcher(prize).matches()) throw new IOException("Invalid tournament prize");
        return new Tournament(id, name, start, end, count, prize);
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

    static final class Tournament implements Serializable {
        private static final long serialVersionUID = 1L;
        final long id;
        final String name;
        final LocalDate start;
        final LocalDate end;
        final Long players;
        final String prize;
        private final String searchName;

        Tournament(long id, String name, LocalDate start, LocalDate end, Long players, String prize) {
            this.id = id;
            this.name = name;
            this.start = start;
            this.end = end;
            this.players = players;
            this.prize = prize;
            this.searchName = searchable(name);
        }
    }
}
