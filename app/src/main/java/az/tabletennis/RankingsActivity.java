package az.tabletennis;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class RankingsActivity extends Activity {
    private static final Locale AZERBAIJANI = Locale.forLanguageTag("az-AZ");
    private RankingsApplication application;
    private RankingsStore store;
    private RankingsSource source;
    private RankingSnapshot snapshot;
    private SavedDataStore<DoublesRanking> doublesStore;
    private SavedData<DoublesRanking> doubles;
    private DoublesRepository doublesRepository;
    private MainScreenChrome chrome;
    private Button retry;
    private Button clearSearch;
    private EditText search;
    private TextView explanation;
    private TextView status;
    private TextView updated;
    private TextView empty;
    private Button refresh;
    private ProgressBar progress;
    private ListView list;
    private ArrayAdapter<String> rows;
    private final List<RankingSnapshot.Player> visiblePlayers = new ArrayList<>();
    private final List<Integer> visibleRanks = new ArrayList<>();
    private final List<DoublesRanking.Row> visiblePairs = new ArrayList<>();
    private boolean loading;
    private boolean doublesLoading;
    private boolean doublesMode;
    private String playerQuery = "";
    private String doublesQuery = "";
    private Parcelable playerPosition;
    private Parcelable doublesPosition;
    private String playerStatus = "";
    private String doublesStatus = "";
    private int resolvedPairs;
    private int totalPairs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        application = (RankingsApplication) getApplication();
        store = new RankingsStore(this);
        source = new RankingsSource(application.websiteUrl());
        doublesStore = new SavedDataStore<>(this, "doubles-ranking", DoublesRanking.class);
        doublesRepository = new DoublesRepository(this, application.websiteUrl());
        snapshot = store.read();
        doubles = doublesStore.read();
        if (state != null) {
            doublesMode = state.getBoolean("doubles");
            playerQuery = state.getString("playerQuery", "");
            doublesQuery = state.getString("doublesQuery", "");
            playerPosition = state.getParcelable("playerPosition");
            doublesPosition = state.getParcelable("doublesPosition");
        }
        createScreen();
        search.setText(doublesMode ? doublesQuery : playerQuery);
        render();
        restorePosition();
        if (state != null && state.getBoolean("drawer")) chrome.openDrawer();
        if (application.doublesRefresh != null) followDoublesRefresh(application.doublesRefresh);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
        refreshIfStale();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        rememberSelection();
        state.putBoolean("drawer", chrome.isDrawerOpen());
        state.putBoolean("doubles", doublesMode);
        state.putString("playerQuery", playerQuery);
        state.putString("doublesQuery", doublesQuery);
        state.putParcelable("playerPosition", playerPosition);
        state.putParcelable("doublesPosition", doublesPosition);
    }

    private void createScreen() {
        chrome = new MainScreenChrome(this, true, this::refreshRankings);
        updated = chrome.updated;
        refresh = chrome.refresh;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Short screens need the full list viewport for wrapping rows at large text sizes.
        android.content.res.Configuration configuration = getResources().getConfiguration();
        boolean scrollingControls = configuration.screenHeightDp < 480
                || (configuration.screenWidthDp <= 360 && configuration.fontScale >= 1.3f);
        LinearLayout controls = scrollingControls ? root : chrome.body;
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton players = new RadioButton(this);
        players.setId(R.id.players_mode);
        players.setText(R.string.players_mode);
        Presentation.segment(players);
        modes.addView(players, new RadioGroup.LayoutParams(0, -2, 1));
        RadioButton pairs = new RadioButton(this);
        pairs.setId(R.id.doubles_mode);
        pairs.setText(R.string.doubles_mode);
        Presentation.segment(pairs);
        modes.addView(pairs, new RadioGroup.LayoutParams(0, -2, 1));
        modes.check(doublesMode ? R.id.doubles_mode : R.id.players_mode);
        modes.setOnCheckedChangeListener((group, checked) -> choose(checked == R.id.doubles_mode));
        modes.setPadding(dp(3), dp(3), dp(3), dp(3));
        modes.setBackground(Presentation.shape(this, R.color.soft));
        LinearLayout.LayoutParams modeLayout = new LinearLayout.LayoutParams(-1, -2);
        modeLayout.bottomMargin = dp(12);
        controls.addView(modes, modeLayout);
        explanation = label(0, 14);
        search = new EditText(this);
        search.setId(R.id.search);
        search.setSingleLine(true);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout.LayoutParams searchLayout = new LinearLayout.LayoutParams(-1, dp(56));
        searchLayout.bottomMargin = dp(8);
        controls.addView(search, searchLayout);
        search.setTextColor(getColor(R.color.text));
        search.setHintTextColor(getColor(R.color.secondary));
        search.setBackground(Presentation.shape(this, R.color.surface));
        search.setPadding(dp(12), 0, dp(12), 0);
        root.addView(explanation);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setContentDescription(getString(R.string.loading));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        status = label(0, 14);
        status.setId(R.id.status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
        retry = new Button(this);
        retry.setId(R.id.retry);
        retry.setText(R.string.retry);
        Presentation.action(retry);
        retry.setOnClickListener(view -> refreshRankings());
        root.addView(retry);
        empty = label(R.string.no_matches, 16);
        empty.setVisibility(View.GONE);
        root.addView(empty);
        clearSearch = new Button(this);
        clearSearch.setId(R.id.clear_search);
        clearSearch.setText(R.string.clear_search);
        Presentation.action(clearSearch);
        clearSearch.setOnClickListener(view -> search.setText(""));
        root.addView(clearSearch);
        list = new ListView(this);
        list.addHeaderView(root, null, false);
        list.setDivider(new android.graphics.drawable.ColorDrawable(getColor(R.color.line)));
        list.setDividerHeight(dp(1));
        list.setId(android.R.id.list);
        rows = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int position) { return doublesMode ? 1 : 0; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                if (!doublesMode) {
                    PlayerRankingRow row = recycled instanceof PlayerRankingRow ? (PlayerRankingRow) recycled : new PlayerRankingRow(RankingsActivity.this);
                    row.bind(visiblePlayers.get(position), visibleRanks.get(position), getItem(position), application);
                    return row;
                }
                DoublesRankingRow row = recycled instanceof DoublesRankingRow ? (DoublesRankingRow) recycled : new DoublesRankingRow(RankingsActivity.this);
                row.bind(visiblePairs.get(position), application);
                return row;
            }
        };
        list.setAdapter(rows);
        list.setOnItemClickListener((parent, view, position, id) -> {
            position -= list.getHeaderViewsCount();
            if (position < 0) return;
            if (doublesMode) startActivity(new android.content.Intent(this, PairResultsActivity.class)
                    .putExtra("pairId", visiblePairs.get(position).pair.id));
            else startActivity(new android.content.Intent(this, ProfileActivity.class)
                    .putExtra("playerId", visiblePlayers.get(position).id));
        });
        chrome.body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(android.text.Editable value) { }
        });
    }

    private void choose(boolean pairs) {
        if (doublesMode == pairs) return;
        rememberSelection();
        doublesMode = pairs;
        search.setText(pairs ? doublesQuery : playerQuery);
        render();
        restorePosition();
        refreshIfStale();
    }

    private void rememberSelection() {
        if (doublesMode) {
            doublesQuery = search.getText().toString();
            doublesPosition = list.onSaveInstanceState();
        } else {
            playerQuery = search.getText().toString();
            playerPosition = list.onSaveInstanceState();
        }
    }

    private void restorePosition() {
        Parcelable position = doublesMode ? doublesPosition : playerPosition;
        if (position != null) list.onRestoreInstanceState(position);
    }

    private void refreshIfStale() {
        long time = doublesMode ? (doubles == null ? 0 : doubles.updatedAt) : (snapshot == null ? 0 : snapshot.updatedAt);
        if (time == 0 || SavedData.needsRefresh(time, application.clock().millis())) refreshRankings();
    }

    private void refreshRankings() {
        if (!application.isOnline()) { renderStatus(); return; }
        if (doublesMode) {
            if (doublesLoading) return;
            CompletableFuture<SavedData<DoublesRanking>> pending = application.doublesRefresh;
            if (pending == null || pending.isDone()) {
                pending = new CompletableFuture<>();
                application.doublesRefresh = pending;
                CompletableFuture<SavedData<DoublesRanking>> started = pending;
                followDoublesRefresh(started);
                application.doublesWorker.execute(() -> retrieveDoubles(started));
            } else followDoublesRefresh(pending);
        } else {
            if (loading) return;
            loading = true;
            playerStatus = "";
            render();
            application.rankingsWorker.execute(this::retrieveRankings);
        }
    }

    private void retrieveRankings() {
        try {
            RankingSnapshot retrieved = new RankingSnapshot(source.fetchAmateurs(), application.clock().millis());
            store.write(retrieved);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                snapshot = retrieved;
                application.photos().refreshed();
                loading = false;
                playerStatus = getString(R.string.refresh_success);
                render();
            });
        } catch (IOException failure) {
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                loading = false;
                playerStatus = getString(snapshot == null ? R.string.unavailable : R.string.saved_offline);
                render();
            });
        }
    }

    private void followDoublesRefresh(CompletableFuture<SavedData<DoublesRanking>> pending) {
        doublesLoading = true;
        totalPairs = 0;
        doublesStatus = "";
        render();
        if (!pending.isDone()) application.doublesProgress = (completed, total) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            resolvedPairs = completed;
            totalPairs = total;
            if (doublesMode) renderStatus();
        });
        pending.whenComplete((retrieved, failure) -> runOnUiThread(() -> {
            if (application.doublesRefresh == pending) {
                application.doublesRefresh = null;
                application.doublesProgress = null;
            }
            if (isDestroyed()) return;
            doublesLoading = false;
            if (failure == null) {
                doubles = retrieved;
                application.photos().refreshed();
                doublesStatus = getString(R.string.refresh_success);
            } else doublesStatus = getString(doubles == null ? R.string.data_unavailable : R.string.data_saved_failure);
            render();
        }));
    }

    private void retrieveDoubles(CompletableFuture<SavedData<DoublesRanking>> pending) {
        try {
            DoublesRanking ranking = doublesRepository.ranking((completed, total) -> {
                DoublesRepository.Progress listener = application.doublesProgress;
                if (listener != null) listener.update(completed, total);
            });
            // The worker serializes refreshes, including those spanning Activity recreation.
            // Read the durable baseline here rather than using an Activity's older copy.
            SavedData<DoublesRanking> previous = doublesStore.read();
            long observedAt = application.clock().millis();
            SavedData<DoublesRanking> retrieved = new SavedData<>(
                    ranking.observedAfter(previous == null ? null : previous.value, observedAt), observedAt);
            doublesStore.write(retrieved);
            application.doublesProgress = null;
            pending.complete(retrieved);
        } catch (IOException failure) {
            application.doublesProgress = null;
            pending.completeExceptionally(failure);
        }
    }

    private void renderStatus() {
        boolean busy = doublesMode ? doublesLoading : loading;
        boolean hasData = doublesMode ? doubles != null : snapshot != null;
        refresh.setEnabled(!busy && application.isOnline());
        search.setEnabled(!doublesMode || hasData || !busy);
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        progress.setIndeterminate(!doublesMode || totalPairs == 0);
        if (doublesMode && totalPairs > 0) {
            progress.setMax(totalPairs);
            progress.setProgress(resolvedPairs);
        }
        if (busy) status.setText(doublesMode && totalPairs > 0
                ? getString(R.string.doubles_loading, resolvedPairs, totalPairs) : getString(R.string.loading));
        else if (!application.isOnline()) status.setText(hasData ? R.string.offline_saved
                : (doublesMode ? R.string.data_unavailable : R.string.unavailable));
        else status.setText(doublesMode ? doublesStatus : playerStatus);
        if (!busy && !hasData && application.isOnline() && status.getText().length() == 0)
            status.setText(doublesMode ? R.string.data_unavailable : R.string.unavailable);
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        retry.setVisibility(!busy && !hasData ? View.VISIBLE : View.GONE);
        retry.setEnabled(application.isOnline());
    }

    private void render() {
        List<String> visible = new ArrayList<>();
        visiblePlayers.clear();
        visibleRanks.clear();
        visiblePairs.clear();
        explanation.setText(doublesMode ? R.string.doubles_explanation : R.string.rank_explanation);
        search.setHint(doublesMode ? R.string.doubles_search_hint : R.string.search_hint);
        search.setContentDescription(search.getHint());
        long time = doublesMode ? (doubles == null ? 0 : doubles.updatedAt) : (snapshot == null ? 0 : snapshot.updatedAt);
        updated.setText("");
        if (time != 0) {
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, AZERBAIJANI);
            updated.setText(getString(R.string.updated_at, format.format(new Date(time))));
        }
        String query = search.getText().toString().trim().toLowerCase(AZERBAIJANI);
        if (doublesMode && doubles != null) {
            for (DoublesRanking.Row row : doubles.value.rows) {
                if (row.pair.first.name.toLowerCase(AZERBAIJANI).contains(query)
                        || row.pair.second.name.toLowerCase(AZERBAIJANI).contains(query)) {
                    visiblePairs.add(row);
                    visible.add(getString(R.string.doubles_row, row.position, row.pair.names(), row.points));
                }
            }
        } else if (!doublesMode && snapshot != null) {
            int rank = 0;
            int previousPoints = -1;
            for (int i = 0; i < snapshot.players.size(); i++) {
                RankingSnapshot.Player player = snapshot.players.get(i);
                if (player.points != previousPoints) rank = i + 1;
                previousPoints = player.points;
                if (player.name.toLowerCase(AZERBAIJANI).contains(query)) {
                    visiblePlayers.add(player);
                    visibleRanks.add(rank);
                    visible.add(getResources().getQuantityString(R.plurals.player_row, player.points, rank, player.name, player.points));
                }
            }
        }
        Parcelable position = list.onSaveInstanceState();
        rows.clear();
        rows.addAll(visible);
        list.onRestoreInstanceState(position);
        empty.setText(doublesMode ? R.string.no_pairs : R.string.no_matches);
        boolean noMatches = time != 0 && visible.isEmpty();
        empty.setVisibility(noMatches ? View.VISIBLE : View.GONE);
        clearSearch.setVisibility(noMatches && !query.isEmpty() ? View.VISIBLE : View.GONE);
        renderStatus();
    }

    private TextView label(int resource, int size) {
        TextView label = Presentation.label(this, resource == 0 ? "" : getString(resource), size);
        label.setTextColor(getColor(R.color.secondary));
        return label;
    }

    // Legacy Back dispatch; MainScreenChrome registers the native gesture callback on API 33+.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        if (!chrome.closeDrawer()) super.onBackPressed();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
