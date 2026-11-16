package az.tabletennis;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

/** Small native-View styling primitives shared by screens as they adopt the design. */
final class Presentation {
    private Presentation() { }

    static void systemBars(android.app.Activity activity) {
        android.view.View decor = activity.getWindow().getDecorView();
        boolean night = (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int flags = decor.getSystemUiVisibility();
        decor.setSystemUiVisibility(night ? flags & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : flags | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView label(Context context, CharSequence text, int size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(context.getColor(R.color.text));
        view.setPadding(0, dp(context, 6), 0, dp(context, 6));
        return view;
    }

    static void action(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextColor(new ColorStateList(new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
                new int[] {context.getColor(R.color.secondary), context.getColor(R.color.primary)}));
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.soft)), null, shape(context, R.color.surface)));
    }

    static void segment(RadioButton button) {
        Context context = button.getContext();
        button.setButtonDrawable(null);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10));
        button.setMinHeight(dp(context, 48));
        button.setTextSize(14);
        button.setTextColor(new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {context.getColor(R.color.on_primary), context.getColor(R.color.text)}));
        android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
        background.addState(new int[] {android.R.attr.state_checked}, shape(context, R.color.primary));
        background.addState(new int[] {}, shape(context, R.color.soft));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.line)), background, null));
    }

    static GradientDrawable shape(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(context.getColor(color));
        drawable.setCornerRadius(dp(context, 12));
        return drawable;
    }
}
