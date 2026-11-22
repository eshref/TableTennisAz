package az.tabletennis;

import az.tabletennis.HeadToHeadSearch.Meeting;
import az.tabletennis.HeadToHeadSearch.Player;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class HeadToHeadSinglesIndex {
    private static final int BUCKETS = 256;
    private final WebsiteClient client;

    HeadToHeadSinglesIndex(WebsiteClient client) {
        this.client = client;
    }

    record Comparison(Instant refreshedAt, List<Meeting> meetings) {}

    Comparison findMeetings(Player first, Player second) throws IOException {
        long lower = Math.min(first.id(), second.id());
        long higher = Math.max(first.id(), second.id());
        int bucket = bucket(lower);
        String data = client.get("singles-index-v1/" + bucket + ".json");
        try {
            JSONObject shard = new JSONObject(data);
            if (shard.getInt("schema") != 1 || shard.getInt("bucket") != bucket)
                throw new IOException("Unexpected singles index document");
            Instant refreshedAt = Instant.parse(shard.getString("refreshedAt"));
            JSONArray rows = shard.getJSONArray("matches");
            List<Meeting> meetings = new ArrayList<>();
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                long id = row.getLong("id");
                JSONObject left = row.getJSONObject("left");
                JSONObject right = row.getJSONObject("right");
                long leftId = left.getLong("id");
                long rightId = right.getLong("id");
                long rowLower = Math.min(leftId, rightId);
                long rowHigher = Math.max(leftId, rightId);
                if (id <= 0 || !ids.add(id) || rowLower <= 0 || rowLower == rowHigher || bucket(rowLower) != bucket)
                    throw new IOException("Invalid singles index identity");

                // Validate the whole bucket before treating a missing matchup as a completed search.
                String leftName = left.getString("name");
                String rightName = right.getString("name");
                String date = row.getString("date");
                MatchSummary.sourceDay(date);
                String score = row.getString("score");
                String event = row.getString("event");
                long eventId = row.getLong("eventId");
                boolean duel = row.getBoolean("duel");
                TournamentStage stage = TournamentStage.fromSource(row.optString("stage"));
                if (leftName.trim().isEmpty() || rightName.trim().isEmpty() || event.trim().isEmpty()
                        || !score.matches("[0-9]+-[0-9]+") || eventId < 0)
                    throw new IOException("Unusable singles index result");
                if (rowLower == lower && rowHigher == higher) {
                    MatchSummary match = new MatchSummary(id, List.of(leftName), List.of(rightName), date,
                            score, eventId, event, duel, false, stage);
                    meetings.add(new Meeting(match, HeadToHeadSearch.winner(score, leftId == first.id())));
                }
            }
            return new Comparison(refreshedAt, meetings);
        } catch (JSONException | DateTimeParseException invalid) {
            throw new IOException("Unusable singles index", invalid);
        }
    }

    private int bucket(long profileId) {
        return (int) (profileId % BUCKETS);
    }
}
