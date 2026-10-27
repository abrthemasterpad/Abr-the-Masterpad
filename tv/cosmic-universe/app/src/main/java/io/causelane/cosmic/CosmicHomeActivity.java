package io.causelane.cosmic;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Standalone Cosmic Universe Android TV app. It is intentionally not a HOME-category activity:
 * install and test as a normal app before considering any default-launcher change.
 */
public final class CosmicHomeActivity extends Activity {
    private CosmicSkyView sky;
    private GridLayout grid;
    private final List<ResolveInfo> launchables = new ArrayList<>();
    private int tileSize;
    private int columns = 5;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        tileSize = (int) (getResources().getDisplayMetrics().density * 112);
        buildUi();
        loadApps();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        sky = new CosmicSkyView(this);
        root.addView(sky, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(42), dp(24), dp(42), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("COSMIC  UNIVERSE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setLetterSpacing(0.12f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("YOUR APPS · YOUR ORBIT");
        subtitle.setTextColor(0xFFB9C8F6);
        subtitle.setTextSize(12);
        subtitle.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = dp(5);
        content.addView(subtitle, subLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(14);
        content.addView(scroll, scrollLp);

        grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));

        TextView footer = new TextView(this);
        footer.setText("OK  OPEN     BACK  EXIT     •     LOCAL ANIMATIONS · NO VIDEO BACKGROUND");
        footer.setTextColor(0xFF9AA9CF);
        footer.setTextSize(10);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-2, -2);
        footLp.topMargin = dp(8);
        content.addView(footer, footLp);
        setContentView(root);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        if (apps == null || apps.isEmpty()) {
            query.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            query.addCategory(Intent.CATEGORY_LAUNCHER);
            apps = pm.queryIntentActivities(query, 0);
        }
        if (apps != null) launchables.addAll(apps);
        Collections.sort(launchables, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });

        // Do not list this prototype or Settings as a privileged/system shortcut.
        for (ResolveInfo info : launchables) {
            if (info.activityInfo == null || getPackageName().equals(info.activityInfo.packageName)) continue;
            grid.addView(createTile(info));
        }
        if (grid.getChildCount() > 0) {
            grid.getChildAt(0).requestFocus();
        } else {
            TextView empty = new TextView(this);
            empty.setText("No launchable apps were found. Install apps or check this TV's app visibility rules.");
            empty.setTextColor(Color.WHITE);
            grid.addView(empty);
        }
    }

    private View createTile(final ResolveInfo info) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setPadding(dp(5), dp(7), dp(5), dp(7));
        tile.setBackground(makeTileBackground(false));
        tile.setContentDescription(String.valueOf(info.loadLabel(getPackageManager())));

        TextView planet = new TextView(this);
        planet.setGravity(Gravity.CENTER);
        planet.setText(info.loadIcon(getPackageManager()) == null ? "✦" : "✦");
        planet.setTextSize(30);
        planet.setTextColor(Color.WHITE);
        GradientDrawable orb = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8B9BFF, 0xFF5B4CB8, 0xFF222B63});
        orb.setShape(GradientDrawable.OVAL);
        orb.setStroke(dp(1), 0xFFB9C8FF);
        orb.setSize(dp(64), dp(64));
        planet.setBackground(orb);
        tile.addView(planet, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView label = new TextView(this);
        label.setText(info.loadLabel(getPackageManager()));
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, dp(34));
        labelLp.topMargin = dp(6);
        tile.addView(label, labelLp);

        tile.setOnFocusChangeListener((v, focused) -> {
            v.setScaleX(focused ? 1.09f : 1f);
            v.setScaleY(focused ? 1.09f : 1f);
            v.setBackground(makeTileBackground(focused));
            planet.animate().rotationBy(focused ? 18f : -18f).setDuration(220).start();
        });
        tile.setOnClickListener(v -> launch(info));
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                launch(info);
                return true;
            }
            return false;
        });

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = tileSize;
        lp.height = dp(128);
        lp.setMargins(dp(5), dp(3), dp(5), dp(3));
        tile.setLayoutParams(lp);
        return tile;
    }

    private GradientDrawable makeTileBackground(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focused ? 0x554F6FC4 : 0x1CFFFFFF);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(focused ? 2 : 1), focused ? 0xFFDCEBFF : 0x337D91D9);
        return d;
    }

    private void launch(ResolveInfo info) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(info.activityInfo.packageName, info.activityInfo.name);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Could not open " + info.loadLabel(getPackageManager()), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (sky != null) sky.setLowPowerMode(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
