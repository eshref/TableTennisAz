package az.tabletennis;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

abstract class SavedScreen<T extends Serializable> extends Activity {
    protected RankingsApplication application;
    protected LinearLayout content;
    protected LinearLayout root;
    protected SavedData<T> saved;
    private SavedDataStore<T> store;
    private TextView status;
    private TextView updated;
    private Button refresh;
    private MainScreenChrome chrome;
    private ScrollView scroll;
    private android.widget.ProgressBar progress;
    private Button retry;
    private Bundle scrollPositions = new Bundle();
    private String selectionKey;
    private boolean loading;
    private int selectionVersion;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Presentation.systemBars(this);
        application = (RankingsApplication) getApplication();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(16));
        root.setBackgroundColor(getColor(R.color.background));
        if (isMainDestination()) {
            chrome = new MainScreenChrome(this, false, () -> refresh(false));
            refresh = chrome.refresh;
            updated = chrome.updated;
            root.setPadding(0, 0, 0, dp(16));
            Bundle positions = state == null ? application.resultsScrollPositions : state.getBundle("scrollPositions");
            if (positions != null) scrollPositions = new Bundle(positions);
        } else {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), dp(16) + insets.getSystemWindowInsetTop(),
                        dp(16) + insets.getSystemWindowInsetRight(), dp(16) + insets.getSystemWindowInsetBottom());
                return insets;
            });
            root.addView(button(getString(R.string.back), () -> finish()));
            refresh = button(getString(R.string.refresh), () -> refresh(false));
            refresh.setId(R.id.refresh);
            root.addView(refresh);
            updated = label("", 13);
            updated.setId(R.id.updated);
            root.addView(updated);
        }
        progress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setContentDescription(getString(R.string.data_loading));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        status = label("", 14);
        status.setId(R.id.status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
        retry = button(getString(R.string.retry), () -> refresh(false));
        retry.setId(R.id.retry);
        retry.setVisibility(View.GONE);
        root.addView(retry);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(this);
        scroll.setId(R.id.main_scroll);
        if (chrome != null) {
            root.addView(content);
            scroll.addView(root);
            chrome.body.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
            if (state != null && state.getBoolean("drawer")) chrome.openDrawer();
        } else {
            scroll.addView(content);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            setContentView(root);
        }

    }

    protected final void select(String key, Class<T> type) {
        rememberScroll();
        selectionKey = key;
        selectionVersion++;
        loading = false;
        refresh.setEnabled(application.isOnline());
        store = new SavedDataStore<>(this, key, type);
        saved = store.read();
        showSaved();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store == null) return;
        showSaved();
        if (saved == null || SavedData.needsRefresh(dataUpdatedAt(), application.clock().millis())) refresh(false);
    }

    protected final void openSelection(String key, Class<T> type) {
        select(key, type);
        if (saved == null || SavedData.needsRefresh(dataUpdatedAt(), application.clock().millis())) refresh(false);
    }

    protected final void refresh(boolean more) {
        if (loading) return;
        if (!application.isOnline()) {
            rememberScroll();
            showSaved();
            return;
        }
        loading = true;
        refresh.setEnabled(false);
        rememberScroll();
        status.setText(R.string.data_loading);
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        int version = selectionVersion;
        SavedDataStore<T> targetStore = store;
        T previous = saved == null ? null : saved.value;
        long previousUpdate = saved == null ? 0 : dataUpdatedAt();
        Fetch<T> request = request(previous, more);
        application.rankingsWorker.execute(() -> {
            try {
                T fetched = request.load();
                SavedData<T> next = new SavedData<>(fetched, retrievalTime(previousUpdate, more));
                targetStore.write(next);
                runOnUiThread(() -> {
                    if (isDestroyed() || version != selectionVersion) return;
                    loading = false;
                    saved = next;
                    showSaved();
                    if (chrome != null) status.setText(R.string.refresh_success);
                    status.setVisibility(View.VISIBLE);
                });
            } catch (IOException failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || version != selectionVersion) return;
                    loading = false;
                    showSaved();
                    status.setText(saved == null ? R.string.data_unavailable : R.string.data_saved_failure);
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    protected boolean isMainDestination() { return false; }

    protected interface Fetch<V> { V load() throws IOException; }
    protected abstract Fetch<T> request(T previous, boolean more);
    protected abstract void render(T value);

    protected long dataUpdatedAt() { return saved.updatedAt; }
    private long retrievalTime(long previousUpdate, boolean more) {
        // An appended page does not refresh the older information already on screen.
        return more && previousUpdate > 0 ? previousUpdate : application.clock().millis();
    }

    protected final void showSaved() {
        content.removeAllViews();
        updated.setText("");
        status.setText(loading ? getString(R.string.data_loading) : application.isOnline() ? ""
                : getString(saved == null ? R.string.data_unavailable : chrome == null ? R.string.data_saved_failure : R.string.offline_saved));
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        retry.setVisibility(!loading && saved == null ? View.VISIBLE : View.GONE);
        retry.setEnabled(application.isOnline());
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        refresh.setEnabled(!loading && application.isOnline());
        if (saved != null) {
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("az-AZ"));
            updated.setText(getString(R.string.updated_at, format.format(new Date(dataUpdatedAt()))));
            render(saved.value);
        }
        if (chrome != null) {
            int position = scrollPositions.getInt(selectionKey, 0);
            scroll.post(() -> scroll.scrollTo(0, position));
        }
    }

    protected final TextView label(String text, int size) {
        return Presentation.label(this, text, size);
    }

    protected final Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        Presentation.action(button);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    @Override protected void onPause() {
        rememberScroll();
        if (chrome != null) application.resultsScrollPositions = new Bundle(scrollPositions);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        rememberScroll();
        state.putBundle("scrollPositions", scrollPositions);
        state.putBoolean("drawer", chrome != null && chrome.isDrawerOpen());
        super.onSaveInstanceState(state);
    }

    // Legacy Back dispatch; MainScreenChrome registers the native gesture callback on API 33+.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        if (chrome == null || !chrome.closeDrawer()) super.onBackPressed();
    }

    private void rememberScroll() {
        if (chrome != null && selectionKey != null) scrollPositions.putInt(selectionKey, scroll.getScrollY());
    }

    protected final int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
