package az.tabletennis;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class RankingsStore {
    private final AtomicFile file;

    RankingsStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "rankings.json"));
    }

    RankingSnapshot read() {
        try {
            JSONObject saved = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            JSONArray rows = saved.getJSONArray("players");
            List<RankingSnapshot.Player> players = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                players.add(new RankingSnapshot.Player(row.getLong("id"), row.getString("name"), row.getInt("points")));
            }
            if (players.isEmpty() || saved.getLong("updatedAt") <= 0) return null;
            return new RankingSnapshot(players, saved.getLong("updatedAt"));
        } catch (IOException | JSONException unusableSnapshot) {
            return null;
        }
    }

    void write(RankingSnapshot snapshot) throws IOException {
        FileOutputStream stream = null;
        try {
            JSONArray rows = new JSONArray();
            for (RankingSnapshot.Player player : snapshot.players) {
                rows.put(new JSONObject().put("id", player.id).put("name", player.name).put("points", player.points));
            }
            byte[] bytes = new JSONObject().put("updatedAt", snapshot.updatedAt).put("players", rows)
                    .toString().getBytes(StandardCharsets.UTF_8);
            stream = file.startWrite();
            stream.write(bytes);
            file.finishWrite(stream);
        } catch (IOException | JSONException failure) {
            if (stream != null) file.failWrite(stream);
            throw new IOException("Could not save rankings", failure);
        }
    }
}
