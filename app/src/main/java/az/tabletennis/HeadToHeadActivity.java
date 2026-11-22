package az.tabletennis;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
    private MainScreenChrome chrome;
    private HeadToHeadSearch source;
    private ScrollView scroll;
    private SearchForm singles;
    private SearchForm doubles;
    private SearchForm active;
    private RadioButton singlesTab;
    private RadioButton doublesTab;
    private LinearLayout fields;
    private Button search;
    private TextView status;
    private ProgressBar progress;
    private LinearLayout results;
    private PlayerField activeField;
    private Runnable pendingQuery;
    private Future<?> directoryRequest;
    private int directoryVersion;
    private enum SearchStatus { IDLE, RUNNING, COMPLETE, INTERRUPTED }

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

        RadioGroup tabs = new RadioGroup(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(Presentation.shape(this, R.color.soft));
        singlesTab = addTab(tabs, R.id.head_to_head_singles, R.string.match_singles);
        doublesTab = addTab(tabs, R.id.head_to_head_doubles, R.string.match_doubles);
        LinearLayout.LayoutParams tabLayout = new LinearLayout.LayoutParams(-1, -2);
        tabLayout.bottomMargin = dp(12);
        form.addView(tabs, tabLayout);

        fields = column();
        form.addView(fields);
        singles = new SearchForm(false);
        doubles = new SearchForm(true);
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
            singles.restore(state, "singles");
            doubles.restore(state, "doubles");
            openedMatchId = state.getLong("opened-match");
            openedMatchOffset = state.getInt("opened-offset");
            if (state.getBoolean("drawer")) chrome.openDrawer();
        }
        selectMode(state != null && state.getBoolean("doubles-mode") ? doubles : singles);
        tabs.check(active == doubles ? doublesTab.getId() : singlesTab.getId());
        tabs.setOnCheckedChangeListener((group, checked) -> selectMode(checked == doublesTab.getId() ? doubles : singles));
        if (active.status == SearchStatus.RUNNING) startSearch();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        singles.save(state, "singles");
        doubles.save(state, "doubles");
        state.putBoolean("doubles-mode", active == doubles);
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

    // Legacy Back dispatch; MainScreenChrome registers the native gesture callback on API 33+.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        if (!chrome.closeDrawer()) super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private RadioButton addTab(RadioGroup tabs, int id, int label) {
        RadioButton tab = new RadioButton(this);
        tab.setId(id);
        tab.setText(label);
        tab.setSaveEnabled(false);
        Presentation.segment(tab);
        tabs.addView(tab, new RadioGroup.LayoutParams(0, -2, 1));
        return tab;
    }

    private void selectMode(SearchForm mode) {
        if (active == mode) return;
        clearSuggestions();
        if (active != null) {
            hideKeyboard();
            openedMatchId = 0;
            scroll.scrollTo(0, 0);
        }
        active = mode;
        fields.removeAllViews();
        fields.addView(mode.inputs);
        results.removeAllViews();
        for (MatchSummary match : mode.matches) {
            results.addView(new MatchCard(this, match, () -> openMatch(match)));
        }
        updateControls();
    }

    private void edit(PlayerField field) {
        if (field.settingText) return;
        field.selected = null;
        clearMatches();
        active.status = SearchStatus.IDLE;
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
                Log.w("HeadToHead", "Could not load player suggestions", failure);
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
        List<HeadToHeadSearch.Player> selected = active.selections();
        if (selected.size() != active.players.size() || hasDuplicatePlayers(selected)) return;
        int sideSize = selected.size() / 2;
        List<HeadToHeadSearch.Player> left = selected.subList(0, sideSize);
        List<HeadToHeadSearch.Player> right = selected.subList(sideSize, selected.size());

        clearSuggestions();
        hideKeyboard();
        if (active.status != SearchStatus.RUNNING) clearMatches();
        active.status = SearchStatus.RUNNING;
        updateControls();

        RankingsApplication application = (RankingsApplication) getApplication();
        worker.execute(() -> {
            try {
                source.findMeetings(left, right, (match, details) -> {
                    try {
                        new SavedDataStore<>(application, "match-" + details.id, MatchDetails.class)
                                .write(new SavedData<>(details, application.clock().millis()));
                    } catch (IOException failure) {
                        Log.w("HeadToHead", "Could not save match details", failure);
                    }
                    main.post(() -> {
                        if (!isDestroyed()) showMatch(match);
                    });
                });
                main.post(() -> finishSearch(SearchStatus.COMPLETE));
            } catch (IOException failure) {
                Log.w("HeadToHead", "Could not finish player histories", failure);
                main.post(() -> finishSearch(SearchStatus.INTERRUPTED));
            }
        });
    }

    private void finishSearch(SearchStatus outcome) {
        if (isDestroyed()) return;
        active.status = outcome;
        updateControls();
    }

    private void clearMatches() {
        active.matches.clear();
        results.removeAllViews();
    }

    private void showMatch(MatchSummary match) {
        List<MatchSummary> matches = active.matches;
        if (matches.stream().anyMatch(existing -> existing.id == match.id)) return;
        int insertion = -Collections.binarySearch(matches, match, NEWEST_FIRST) - 1;
        matches.add(insertion, match);
        results.addView(new MatchCard(this, match, () -> openMatch(match)), insertion);
    }

    private void openMatch(MatchSummary match) {
        openedMatchId = match.id;
        openedMatchOffset = matchTop(active.matches.indexOf(match)) - scroll.getScrollY();
        startActivity(new Intent(this, MatchActivity.class).putExtra("matchId", match.id));
    }

    private void restoreMatchPosition() {
        if (openedMatchId == 0) return;
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                List<MatchSummary> matches = active.matches;
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
        boolean running = active.status == SearchStatus.RUNNING;
        List<HeadToHeadSearch.Player> selected = active.selections();
        boolean duplicate = hasDuplicatePlayers(selected);
        for (PlayerField field : active.players) field.input.setEnabled(!running);
        singlesTab.setEnabled(!running);
        doublesTab.setEnabled(!running);
        search.setEnabled(selected.size() == active.players.size() && !duplicate && !running);
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        int message = switch (active.status) {
            case IDLE -> duplicate ? active.duplicateMessage : 0;
            case RUNNING -> R.string.head_to_head_running;
            case COMPLETE -> active.matches.isEmpty() ? R.string.head_to_head_empty : R.string.head_to_head_complete;
            case INTERRUPTED -> R.string.head_to_head_interrupted;
        };
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) status.setText(message);
    }

    private boolean hasDuplicatePlayers(List<HeadToHeadSearch.Player> selected) {
        HashSet<Long> ids = new HashSet<>();
        for (HeadToHeadSearch.Player player : selected) if (!ids.add(player.id())) return true;
        return false;
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

    private final class SearchForm {
        final LinearLayout inputs = column();
        final List<PlayerField> players = new ArrayList<>();
        final ArrayList<MatchSummary> matches = new ArrayList<>();
        final int duplicateMessage;
        SearchStatus status = SearchStatus.IDLE;

        SearchForm(boolean doubles) {
            if (doubles) {
                duplicateMessage = R.string.head_to_head_four_distinct_players;
                addPair(1, R.id.head_to_head_left_first, R.id.head_to_head_left_second);
                addPair(2, R.id.head_to_head_right_first, R.id.head_to_head_right_second);
            } else {
                duplicateMessage = R.string.head_to_head_distinct_players;
                players.add(new PlayerField(inputs, R.id.head_to_head_first, R.string.head_to_head_first, null));
                players.add(new PlayerField(inputs, R.id.head_to_head_second, R.string.head_to_head_second, null));
            }
        }

        private void addPair(int side, int firstId, int secondId) {
            LinearLayout pair = column();
            pair.setPadding(dp(12), dp(8), dp(12), dp(8));
            pair.setBackground(Presentation.shape(HeadToHeadActivity.this, R.color.soft));
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.bottomMargin = dp(8);
            inputs.addView(pair, layout);
            TextView title = Presentation.label(HeadToHeadActivity.this, getString(R.string.head_to_head_pair, side), 16);
            if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
            pair.addView(title);
            players.add(new PlayerField(pair, firstId, R.string.head_to_head_first,
                    getString(R.string.head_to_head_pair_player, side, getString(R.string.head_to_head_first))));
            players.add(new PlayerField(pair, secondId, R.string.head_to_head_second,
                    getString(R.string.head_to_head_pair_player, side, getString(R.string.head_to_head_second))));
        }

        List<HeadToHeadSearch.Player> selections() {
            List<HeadToHeadSearch.Player> selected = new ArrayList<>();
            for (PlayerField field : players) if (field.selected != null) selected.add(field.selected);
            return selected;
        }

        void save(Bundle state, String key) {
            for (int index = 0; index < players.size(); index++) players.get(index).save(state, key + index);
            state.putSerializable(key + "-matches", new ArrayList<>(matches));
            state.putSerializable(key + "-status", status);
        }

        void restore(Bundle state, String key) {
            for (int index = 0; index < players.size(); index++) players.get(index).restore(state, key + index);
            @SuppressWarnings("unchecked")
            List<MatchSummary> saved = (List<MatchSummary>) state.getSerializable(key + "-matches");
            if (saved != null) matches.addAll(saved);
            SearchStatus savedStatus = (SearchStatus) state.getSerializable(key + "-status");
            if (savedStatus != null) status = savedStatus;
        }
    }

    private final class PlayerField {
        final EditText input;
        final LinearLayout suggestions;
        HeadToHeadSearch.Player selected;
        boolean settingText;

        PlayerField(LinearLayout form, int id, int label, String accessibleLabel) {
            LinearLayout group = column();
            form.addView(group);
            TextView name = Presentation.label(HeadToHeadActivity.this, getString(label), 14);
            name.setLabelFor(id);
            name.setContentDescription(accessibleLabel);
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
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
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
