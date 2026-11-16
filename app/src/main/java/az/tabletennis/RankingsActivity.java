package az.tabletennis;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RankingsActivity extends Activity {
    private static final Locale AZERBAIJANI = Locale.forLanguageTag("az-AZ");
    private RankingsApplication application;
    private RankingsStore store;
    private RankingsSource source;
    private RankingSnapshot snapshot;
    private EditText search;
    private TextView status;
    private TextView updated;
    private TextView empty;
    private Button refresh;
    private ProgressBar progress;
    private ArrayAdapter<String> rows;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        application = (RankingsApplication) getApplication();
        store = new RankingsStore(this);
        source = new RankingsSource(application.websiteUrl());
        createScreen();
        if (state != null) search.setText(state.getString("query", ""));
        setLoading(true);
        application.rankingsWorker.execute(() -> {
            RankingSnapshot saved = store.read();
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                snapshot = saved;
                render();
            });
            retrieveRankings();
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("query", search.getText().toString());
    }

    private void createScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 250, 248));
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = label(R.string.rankings_title, 28);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        root.addView(title);
        root.addView(label(R.string.rank_explanation, 14));
        search = new EditText(this);
        search.setId(R.id.search);
        search.setSingleLine(true);
        search.setHint(R.string.search_hint);
        search.setContentDescription(getString(R.string.search_hint));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(56)));
        refresh = new Button(this);
        refresh.setId(R.id.refresh);
        refresh.setText(R.string.refresh);
        refresh.setOnClickListener(view -> {
            setLoading(true);
            application.rankingsWorker.execute(this::retrieveRankings);
        });
        root.addView(refresh);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        updated = label(0, 13);
        updated.setId(R.id.updated);
        root.addView(updated);
        status = label(0, 14);
        status.setId(R.id.status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
        ListView list = new ListView(this);
        list.setId(android.R.id.list);
        rows = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                row.setTextColor(Color.rgb(22, 44, 40));
                row.setTextSize(18);
                row.setMinHeight(dp(64));
                row.setPadding(dp(4), dp(12), dp(4), dp(12));
                return row;
            }
        };
        list.setAdapter(rows);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = label(R.string.no_matches, 16);
        empty.setVisibility(View.GONE);
        root.addView(empty);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable value) { }
        });
        setContentView(root);
    }

    private void retrieveRankings() {
        try {
            RankingSnapshot retrieved = new RankingSnapshot(source.fetch(), application.clock().millis());
            store.write(retrieved);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                snapshot = retrieved;
                status.setText("");
                setLoading(false);
                render();
            });
        } catch (IOException failure) {
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                setLoading(false);
                status.setText(snapshot == null ? R.string.unavailable : R.string.saved_offline);
                render();
            });
        }
    }

    private void setLoading(boolean loading) {
        refresh.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        if (loading) status.setText(R.string.loading);
    }

    private void render() {
        List<String> visible = new ArrayList<>();
        if (snapshot != null) {
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, AZERBAIJANI);
            updated.setText(getString(R.string.updated_at, format.format(new Date(snapshot.updatedAt))));
            String query = search.getText().toString().trim().toLowerCase(AZERBAIJANI);
            int rank = 0;
            int previousPoints = -1;
            for (int i = 0; i < snapshot.players.size(); i++) {
                RankingSnapshot.Player player = snapshot.players.get(i);
                if (player.points != previousPoints) rank = i + 1;
                previousPoints = player.points;
                if (player.name.toLowerCase(AZERBAIJANI).contains(query)) {
                    visible.add(getResources().getQuantityString(R.plurals.player_row, player.points, rank, player.name, player.points));
                }
            }
        }
        rows.clear();
        rows.addAll(visible);
        empty.setVisibility(snapshot != null && visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private TextView label(int string, int size) {
        TextView label = new TextView(this);
        if (string != 0) label.setText(string);
        label.setTextSize(size);
        label.setTextColor(Color.rgb(22, 44, 40));
        label.setPadding(0, dp(6), 0, dp(6));
        return label;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
