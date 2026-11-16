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
    private boolean loading;
    private int selectionVersion;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        application = (RankingsApplication) getApplication();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(android.graphics.Color.rgb(245, 250, 248));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), dp(16) + insets.getSystemWindowInsetTop(),
                    dp(16) + insets.getSystemWindowInsetRight(), dp(16) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        Button back = button(getString(R.string.back), () -> finish());
        root.addView(back);
        refresh = button(getString(R.string.refresh), () -> refresh(false));
        refresh.setId(R.id.refresh);
        root.addView(refresh);
        updated = label("", 13);
        updated.setId(R.id.updated);
        root.addView(updated);
        status = label("", 14);
        status.setId(R.id.status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    protected final void select(String key, Class<T> type) {
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
        if (loading || !application.isOnline()) return;
        loading = true;
        refresh.setEnabled(false);
        status.setText(R.string.data_loading);
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
                });
            } catch (IOException failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || version != selectionVersion) return;
                    loading = false;
                    showSaved();
                    status.setText(saved == null ? R.string.data_unavailable : R.string.data_saved_failure);
                });
            }
        });
    }

    protected interface Fetch<V> { V load() throws IOException; }
    protected abstract Fetch<T> request(T previous, boolean more);
    protected abstract void render(T value);

    protected long dataUpdatedAt() { return saved.updatedAt; }
    protected long retrievalTime(long previousUpdate, boolean more) { return application.clock().millis(); }

    protected final void showSaved() {
        content.removeAllViews();
        updated.setText("");
        status.setText(application.isOnline() ? "" : getString(saved == null ? R.string.data_unavailable : R.string.data_saved_failure));
        refresh.setEnabled(!loading && application.isOnline());
        if (saved != null) {
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("az-AZ"));
            updated.setText(getString(R.string.updated_at, format.format(new Date(dataUpdatedAt()))));
            render(saved.value);
        }
    }

    protected final TextView label(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(android.graphics.Color.rgb(22, 44, 40));
        label.setPadding(0, dp(6), 0, dp(6));
        return label;
    }

    protected final Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    protected final int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
