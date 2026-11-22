package az.tabletennis;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class HeadToHeadActivity extends Activity {
    private static final Comparator<MatchSummary> NEWEST_FIRST = Comparator
            .comparing((MatchSummary match) -> MatchSummary.sourceDay(match.date)).reversed()
            .thenComparingLong(match -> match.id);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<MatchSummary> matches = new ArrayList<>();
    private MainScreenChrome chrome;
    private HeadToHeadSearch source;
    private ScrollView scroll;
    private PlayerField first;
    private PlayerField second;
    private Button search;
    private TextView status;
    private ProgressBar progress;
    private LinearLayout results;
    private PlayerField activeField;
    private Runnable pendingQuery;
    private Future<?> directoryRequest;
    private int directoryVersion;
    private enum SearchStatus { IDLE, RUNNING, COMPLETE, INTERRUPTED }

    private SearchStatus searchStatus = SearchStatus.IDLE;
    private long openedMatchId;
    private int openedMatchOffset;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        source = new HeadToHeadSearch(((RankingsApplication) getApplication()).websiteClient());
        chrome = new MainScreenChrome(this, MainScreenChrome.Destination.HEAD_TO_HEAD, null);

        scroll = new ScrollView(this);
        scroll.setId(R.id.main_scroll);
        scroll.setFillViewport(true);
        LinearLayout form = column();
        scroll.addView(form);
        chrome.body.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        RadioButton singles = new RadioButton(this);
        singles.setText(R.string.match_singles);
        Presentation.segment(singles);
        singles.setChecked(true);
        singles.setClickable(false);
        form.addView(singles, new LinearLayout.LayoutParams(-1, -2));

        first = new PlayerField(form, R.id.head_to_head_first, R.string.head_to_head_first);
        second = new PlayerField(form, R.id.head_to_head_second, R.string.head_to_head_second);
        search = button(R.string.head_to_head_search, this::startSearch);
        form.addView(search);
        status = Presentation.label(this, "", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        form.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        results = column();
        form.addView(results);

        if (state != null) {
            first.restore(state, "first");
            second.restore(state, "second");
            @SuppressWarnings("unchecked")
            List<MatchSummary> saved = (List<MatchSummary>) state.getSerializable("matches");
            if (saved != null) for (MatchSummary match : saved) showMatch(match);
            searchStatus = (SearchStatus) state.getSerializable("status");
            openedMatchId = state.getLong("opened-match");
            openedMatchOffset = state.getInt("opened-offset");
            if (searchStatus == SearchStatus.RUNNING) startSearch();
            if (state.getBoolean("drawer")) chrome.openDrawer();
        }
        updateControls();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        first.save(state, "first");
        second.save(state, "second");
        state.putSerializable("matches", new ArrayList<>(matches));
        state.putSerializable("status", searchStatus);
        state.putLong("opened-match", openedMatchId);
        state.putInt("opened-offset", openedMatchOffset);
        state.putBoolean("drawer", chrome.isDrawerOpen());
    }

    @Override protected void onResume() {
        super.onResume();
        chrome.observeTournamentDots();
        restoreMatchPosition();
    }

    @Override protected void onPause() {
        chrome.stopObservingTournamentDots();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (!chrome.closeDrawer()) super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void edit(PlayerField field) {
        if (field.settingText) return;
        field.selected = null;
        clearMatches();
        searchStatus = SearchStatus.IDLE;
        updateControls();
        suggest(field);
    }

    private void suggest(PlayerField field) {
        clearSuggestions();
        activeField = field;
        String query = field.input.getText().toString().trim();
        if (query.isEmpty()) return;
        if (query.codePointCount(0, query.length()) < 3) {
            field.suggestions.addView(Presentation.label(this, getString(R.string.head_to_head_short_query), 13));
            return;
        }
        int version = directoryVersion;
        pendingQuery = () -> directoryRequest = worker.submit(() -> {
            try {
                List<HeadToHeadSearch.Player> players = source.findPlayers(query);
                main.post(() -> {
                    if (isDestroyed() || version != directoryVersion) return;
                    showSuggestions(field, players);
                });
            } catch (IOException failure) {
                main.post(() -> {
                    if (isDestroyed() || version != directoryVersion) return;
                    field.suggestions.addView(Presentation.label(this, getString(R.string.head_to_head_directory_failed), 14));
                    field.suggestions.addView(button(R.string.retry, () -> suggest(field)));
                });
            }
        });
        main.postDelayed(pendingQuery, 250);
    }

    private void showSuggestions(PlayerField field, List<HeadToHeadSearch.Player> players) {
        if (players.isEmpty()) {
            field.suggestions.addView(Presentation.label(this, getString(R.string.no_matches), 14));
            return;
        }
        for (HeadToHeadSearch.Player player : players) {
            Button suggestion = new Button(this);
            Presentation.action(suggestion);
            boolean duplicateName = players.stream().anyMatch(other ->
                    other.id() != player.id() && other.name().equals(player.name()));
            suggestion.setText(duplicateName ? getString(R.string.head_to_head_named_profile, player.name(), player.id()) : player.name());
            suggestion.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            suggestion.setOnClickListener(view -> {
                field.selected = player;
                field.setText(player.name());
                clearSuggestions();
                hideKeyboard();
                updateControls();
            });
            field.suggestions.addView(suggestion, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void clearSuggestions() {
        directoryVersion++;
        if (pendingQuery != null) main.removeCallbacks(pendingQuery);
        if (directoryRequest != null) directoryRequest.cancel(true);
        if (activeField != null) activeField.suggestions.removeAllViews();
        activeField = null;
    }

    private void startSearch() {
        HeadToHeadSearch.Player left = first.selected;
        HeadToHeadSearch.Player right = second.selected;
        if (left == null || right == null || left.id() == right.id()) return;

        clearSuggestions();
        hideKeyboard();
        if (searchStatus != SearchStatus.RUNNING) clearMatches();
        searchStatus = SearchStatus.RUNNING;
        updateControls();

        RankingsApplication application = (RankingsApplication) getApplication();
        worker.execute(() -> {
            try {
                source.findSingles(left, right, (match, details) -> {
                    try {
                        new SavedDataStore<>(application, "match-" + details.id, MatchDetails.class)
                                .write(new SavedData<>(details, application.clock().millis()));
                    } catch (IOException failure) {
                        android.util.Log.w("HeadToHead", "Could not save match details", failure);
                    }
                    main.post(() -> {
                        if (!isDestroyed()) showMatch(match);
                    });
                });
                main.post(() -> finishSearch(SearchStatus.COMPLETE));
            } catch (IOException failure) {
                main.post(() -> finishSearch(SearchStatus.INTERRUPTED));
            }
        });
    }

    private void finishSearch(SearchStatus outcome) {
        if (isDestroyed()) return;
        searchStatus = outcome;
        updateControls();
    }

    private void clearMatches() {
        matches.clear();
        results.removeAllViews();
    }

    private void showMatch(MatchSummary match) {
        if (matches.stream().anyMatch(existing -> existing.id == match.id)) return;
        int insertion = -java.util.Collections.binarySearch(matches, match, NEWEST_FIRST) - 1;
        matches.add(insertion, match);
        results.addView(new MatchCard(this, match, () -> openMatch(match)), insertion);
    }

    private void openMatch(MatchSummary match) {
        openedMatchId = match.id;
        openedMatchOffset = matchTop(matches.indexOf(match)) - scroll.getScrollY();
        startActivity(new Intent(this, MatchActivity.class).putExtra("matchId", match.id));
    }

    private void restoreMatchPosition() {
        if (openedMatchId == 0) return;
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                for (int index = 0; index < matches.size(); index++) {
                    if (matches.get(index).id == openedMatchId) {
                        scroll.scrollTo(0, matchTop(index) - openedMatchOffset);
                        break;
                    }
                }
                openedMatchId = 0;
                return true;
            }
        });
    }

    private int matchTop(int index) {
        return results.getTop() + results.getChildAt(index).getTop();
    }

    private void updateControls() {
        boolean running = searchStatus == SearchStatus.RUNNING;
        boolean valid = first.selected != null && second.selected != null;
        boolean duplicate = valid && first.selected.id() == second.selected.id();
        first.input.setEnabled(!running);
        second.input.setEnabled(!running);
        search.setEnabled(valid && !duplicate && !running);
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        int message = switch (searchStatus) {
            case IDLE -> duplicate ? R.string.head_to_head_distinct_players : 0;
            case RUNNING -> R.string.head_to_head_running;
            case COMPLETE -> matches.isEmpty() ? R.string.head_to_head_empty : R.string.head_to_head_complete;
            case INTERRUPTED -> R.string.head_to_head_interrupted;
        };
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) status.setText(message);
    }

    private void hideKeyboard() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(scroll.getWindowToken(), 0);
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private Button button(int label, Runnable action) {
        Button button = new Button(this);
        Presentation.action(button);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) { return Presentation.dp(this, value); }

    private final class PlayerField {
        final EditText input;
        final LinearLayout suggestions;
        HeadToHeadSearch.Player selected;
        boolean settingText;

        PlayerField(LinearLayout form, int id, int label) {
            LinearLayout group = column();
            form.addView(group);
            TextView name = Presentation.label(HeadToHeadActivity.this, getString(label), 14);
            name.setLabelFor(id);
            group.addView(name);
            input = new EditText(HeadToHeadActivity.this);
            input.setId(id);
            input.setSaveEnabled(false);
            input.setSingleLine(true);
            input.setMinHeight(dp(48));
            input.setHint(R.string.head_to_head_name_hint);
            input.setTextColor(getColor(R.color.text));
            input.setHintTextColor(getColor(R.color.secondary));
            input.setPadding(dp(12), 0, dp(12), 0);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            input.setBackground(Presentation.shape(HeadToHeadActivity.this, R.color.surface));
            group.addView(input);

            suggestions = column();
            group.addView(suggestions);
            input.setOnFocusChangeListener((view, focused) -> {
                if (focused && selected == null && activeField != this) suggest(this);
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) { edit(PlayerField.this); }
                @Override public void afterTextChanged(Editable text) { }
            });
        }

        void setText(String text) {
            settingText = true;
            input.setText(text);
            input.setSelection(input.length());
            settingText = false;
        }

        void save(Bundle state, String key) {
            state.putString(key, input.getText().toString());
            state.putSerializable(key + "-selection", selected);
        }

        void restore(Bundle state, String key) {
            selected = (HeadToHeadSearch.Player) state.getSerializable(key + "-selection");
            setText(state.getString(key, ""));
        }
    }
}
