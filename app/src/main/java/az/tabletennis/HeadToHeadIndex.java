package az.tabletennis;

import az.tabletennis.HeadToHeadSearch.Meeting;
import az.tabletennis.HeadToHeadSearch.Player;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class HeadToHeadIndex {
    private static final int BUCKETS = 256;
    private final WebsiteClient client;

    HeadToHeadIndex(WebsiteClient client) {
        this.client = client;
    }

    record Comparison(Instant refreshedAt, List<Meeting> meetings) {}

    private record Side(List<Long> ids, List<String> names) {}

    Comparison findMeetings(List<Player> first, List<Player> second) throws IOException {
        List<Long> firstIds = profileIds(first);
        List<Long> secondIds = profileIds(second);
        int bucket = bucket(firstIds, secondIds);
        boolean doubles = first.size() == 2;
        String directory = doubles ? "doubles-index-v1" : "singles-index-v1";
        String data = client.get(directory + "/" + bucket + ".json");
        try {
            JSONObject shard = new JSONObject(data);
            if (shard.getInt("schema") != 1 || shard.getInt("bucket") != bucket)
                throw new IOException("Unexpected head-to-head index document");
            Instant refreshedAt = Instant.parse(shard.getString("refreshedAt"));
            JSONArray rows = shard.getJSONArray("matches");
            List<Meeting> meetings = new ArrayList<>();
            Set<Long> matchIds = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                long id = row.getLong("id");
                Side left = readSide(row, "left", doubles);
                Side right = readSide(row, "right", doubles);
                if (id <= 0 || !matchIds.add(id) || !Collections.disjoint(left.ids(), right.ids())
                        || bucket(left.ids(), right.ids()) != bucket)
                    throw new IOException("Invalid head-to-head index identity");

                // Validate the whole bucket before treating a missing matchup as a completed search.
                String date = row.getString("date");
                MatchSummary.sourceDay(date);
                String score = row.getString("score");
                String event = row.getString("event");
                long eventId = row.getLong("eventId");
                boolean duel = row.getBoolean("duel");
                TournamentStage stage = TournamentStage.fromSource(row.optString("stage"));
                if (event.trim().isEmpty() || !score.matches("[0-9]+-[0-9]+") || eventId < 0)
                    throw new IOException("Unusable head-to-head index result");

                boolean firstOnLeft = left.ids().equals(firstIds) && right.ids().equals(secondIds);
                boolean firstOnRight = left.ids().equals(secondIds) && right.ids().equals(firstIds);
                if (firstOnLeft || firstOnRight) {
                    MatchSummary match = new MatchSummary(id, left.names(), right.names(), date,
                            score, eventId, event, duel, false, stage);
                    meetings.add(new Meeting(match, HeadToHeadSearch.winner(score, firstOnLeft)));
                }
            }
            return new Comparison(refreshedAt, meetings);
        } catch (JSONException | DateTimeParseException invalid) {
            throw new IOException("Unusable head-to-head index", invalid);
        }
    }

    private Side readSide(JSONObject row, String name, boolean doubles) throws JSONException, IOException {
        JSONArray players = doubles ? row.getJSONArray(name) : new JSONArray().put(row.getJSONObject(name));
        if (players.length() != (doubles ? 2 : 1)) throw new IOException("Unexpected head-to-head side size");
        List<Long> ids = new ArrayList<>(players.length());
        List<String> names = new ArrayList<>(players.length());
        for (int i = 0; i < players.length(); i++) {
            JSONObject player = players.getJSONObject(i);
            long id = player.getLong("id");
            String playerName = player.getString("name");
            if (id <= 0 || ids.contains(id) || playerName.trim().isEmpty())
                throw new IOException("Unusable head-to-head player");
            ids.add(id);
            names.add(playerName);
        }
        Collections.sort(ids);
        return new Side(ids, names);
    }

    private List<Long> profileIds(List<Player> players) {
        return players.stream().map(Player::id).sorted().collect(Collectors.toList());
    }

    private int bucket(List<Long> firstIds, List<Long> secondIds) {
        return (int) (Math.min(firstIds.get(0), secondIds.get(0)) % BUCKETS);
    }
}
