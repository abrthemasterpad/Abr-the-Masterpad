package io.causelane.cosmic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;

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
    private ImageView wallpaperView;
    private GridLayout grid;
    private final List<ResolveInfo> launchables = new ArrayList<>();
    private int tileSize;
    private int columns = 5;
    private FrameLayout root;
    private RemoteDrawingView drawingBoard;
    private RemoteControlServer remoteServer;
    private WorkspaceWidgetLayer workspace;
    private TextView pairingStatus;
    private String pairingDetails = "Waiting for TV network…";
    private LinearLayout pairPanel;
    private final List<View> appTiles = new ArrayList<>();
    private byte[] pairingKey;
    private float remoteDxAccumulator;
    private float remoteDyAccumulator;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        tileSize = (int) (getResources().getDisplayMetrics().density * 112);
        buildUi();
        loadApps();
        startRemoteServer();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        sky = new CosmicSkyView(this);
        root.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setVisibility(View.GONE);
        root.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(42), dp(24), dp(42), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("NEXORA  TV");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setLetterSpacing(0.12f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView workspaceButton = headerButton("WORKSPACE");
        workspaceButton.setOnClickListener(v -> {
            boolean show = workspace.getVisibility() != View.VISIBLE;
            workspace.setVisibility(show ? View.VISIBLE : View.GONE);
            workspaceButton.setText(show ? "CLOSE WORKSPACE" : "WORKSPACE");
        });
        header.addView(workspaceButton);
        TextView wallpaperButton = headerButton("WALLPAPERS");
        wallpaperButton.setOnClickListener(v -> showWallpaperPicker());
        LinearLayout.LayoutParams wallpaperLp = new LinearLayout.LayoutParams(-2, -2);
        wallpaperLp.leftMargin = dp(8);
        header.addView(wallpaperButton, wallpaperLp);
        TextView pairingButton = headerButton("PAIR REMOTE");
        pairingButton.setOnClickListener(v -> {
            boolean show = pairPanel.getVisibility() != View.VISIBLE;
            pairPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) pairPanel.bringToFront();
            pairingButton.setText(show ? "HIDE PAIRING" : "PAIR REMOTE");
        });
        LinearLayout.LayoutParams pairingButtonLp = new LinearLayout.LayoutParams(-2, -2);
        pairingButtonLp.leftMargin = dp(8);
        header.addView(pairingButton, pairingButtonLp);
        content.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("NEXORA · YOUR TV, YOUR SPACE");
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
        footer.setText("OK  OPEN     BACK  EXIT     •     OFFLINE WALLPAPERS · LOCAL-FIRST");
        footer.setTextColor(0xFF9AA9CF);
        footer.setTextSize(10);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-2, -2);
        footLp.topMargin = dp(8);
        content.addView(footer, footLp);
        pairPanel = new LinearLayout(this);
        pairPanel.setOrientation(LinearLayout.HORIZONTAL);
        pairPanel.setGravity(Gravity.CENTER_VERTICAL);
        pairPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        pairPanel.setBackground(makeTileBackground(false));
        ImageView qr = new ImageView(this);
        qr.setTag("pairing-qr");
        pairPanel.addView(qr, new LinearLayout.LayoutParams(dp(94), dp(94)));
        pairingStatus = new TextView(this);
        pairingStatus.setText("Starting local remote…");
        pairingStatus.setTextColor(Color.WHITE);
        pairingStatus.setTextSize(10);
        pairingStatus.setPadding(dp(8), 0, dp(4), 0);
        pairPanel.addView(pairingStatus, new LinearLayout.LayoutParams(dp(160), -2));
        FrameLayout.LayoutParams pairLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        pairLp.setMargins(dp(8), dp(8), dp(24), dp(20));
        pairPanel.setVisibility(View.GONE);
        root.addView(pairPanel, pairLp);
        workspace = new WorkspaceWidgetLayer(this);
        workspace.setVisibility(View.GONE);
        root.addView(workspace, new FrameLayout.LayoutParams(-1, -1));
        drawingBoard = new RemoteDrawingView(this);
        drawingBoard.setVisibility(View.GONE);
        FrameLayout.LayoutParams drawLp = new FrameLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.68f), (int)(getResources().getDisplayMetrics().heightPixels * 0.66f), Gravity.CENTER);
        root.addView(drawingBoard, drawLp);
        drawingBoard.bringToFront();
        setContentView(root);
        applySavedWallpaper();
    }

    private static final String[] WALLPAPER_LABELS = {
            "Animated default", "Mountain Sunrise", "Beach Paradise", "Rainy City", "Snowy Winter",
            "Autumn Forest", "City Lights", "Wildlife Safari", "Lotus Pond", "Summer Sunflowers", "Abstract Flow"
    };
    private static final String[] WALLPAPER_RESOURCES = {
            "", "mountain_sunrise", "beach_summer", "rainy_window", "snow_winter",
            "autumn_forest", "city_night", "wildlife_lion", "nature_lotus", "summer_field", "abstract_flow"
    };

    private void showWallpaperPicker() {
        new AlertDialog.Builder(this)
                .setTitle("Choose your wallpaper")
                .setItems(WALLPAPER_LABELS, (dialog, which) -> {
                    getSharedPreferences("nexora_settings", MODE_PRIVATE).edit().putInt("wallpaper_index", which).apply();
                    applyWallpaper(which);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applySavedWallpaper() {
        int selected = getSharedPreferences("nexora_settings", MODE_PRIVATE).getInt("wallpaper_index", 0);
        applyWallpaper(Math.max(0, Math.min(selected, WALLPAPER_LABELS.length - 1)));
    }

    private void applyWallpaper(int index) {
        if (index <= 0) {
            wallpaperView.setVisibility(View.GONE);
            sky.setVisibility(View.VISIBLE);
            return;
        }
        String resourceName = WALLPAPER_RESOURCES[index];
        int resourceId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
        if (resourceId == 0) {
            wallpaperView.setVisibility(View.GONE);
            sky.setVisibility(View.VISIBLE);
            new AlertDialog.Builder(this).setTitle("Wallpaper unavailable")
                    .setMessage("This wallpaper was not included in this APK build. Rebuild the complete asset bundle.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        wallpaperView.setImageResource(resourceId);
        wallpaperView.setContentDescription(WALLPAPER_LABELS[index]);
        wallpaperView.setVisibility(View.VISIBLE);
        sky.setVisibility(View.GONE);
    }

    private TextView headerButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(makeTileBackground(false));
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        // Query both TV and standard launcher surfaces, then deduplicate by stable component identity.
        Map<String, ResolveInfo> unique = new java.util.LinkedHashMap<>();
        Intent tvQuery = new Intent(Intent.ACTION_MAIN);
        tvQuery.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvApps = pm.queryIntentActivities(tvQuery, 0);
        if (tvApps != null) for (ResolveInfo info : tvApps) {
            if (info.activityInfo != null) unique.put(info.activityInfo.packageName + "/" + info.activityInfo.name, info);
        }
        Intent mobileQuery = new Intent(Intent.ACTION_MAIN);
        mobileQuery.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> mobileApps = pm.queryIntentActivities(mobileQuery, 0);
        if (mobileApps != null) for (ResolveInfo info : mobileApps) {
            if (info.activityInfo != null) unique.putIfAbsent(info.activityInfo.packageName + "/" + info.activityInfo.name, info);
        }
        launchables.addAll(unique.values());
        Collections.sort(launchables, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });

        // Do not list this prototype or Settings as a privileged/system shortcut.
        for (ResolveInfo info : launchables) {
            if (info.activityInfo == null || getPackageName().equals(info.activityInfo.packageName)) continue;
            View tile = createTile(info);
            appTiles.add(tile);
            grid.addView(tile);
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


    private int themedIconForLabel(String label) {
        String resource = null;
        if (label.contains("setting")) resource = "icon_settings";
        else if (label.contains("file") || label.contains("manager") || label.contains("storage")) resource = "icon_files";
        else if (label.contains("gallery") || label.contains("photo") || label.contains("image")) resource = "icon_gallery";
        else if (label.contains("camera")) resource = "icon_camera";
        else if (label.contains("music") || label.contains("audio")) resource = "icon_music";
        else if (label.contains("calculator")) resource = "icon_calculator";
        else if (label.contains("clock") || label.contains("alarm")) resource = "icon_clock";
        else if (label.contains("weather")) resource = "icon_weather";
        else if (label.contains("note")) resource = "icon_notes";
        else if (label.contains("task") || label.contains("todo")) resource = "icon_tasks";
        else if (label.contains("browser") || label.equals("internet") || label.contains("chrome")) resource = "icon_browser";
        else if (label.contains("bluetooth")) resource = "icon_bluetooth";
        else if (label.contains("game")) resource = "icon_games";
        else if (label.contains("news")) resource = "icon_news";
        else if (label.contains("calendar")) resource = "icon_calendar";
        else if (label.equals("apps") || label.contains("app drawer")) resource = "icon_apps";
        return resource == null ? 0 : getResources().getIdentifier(resource, "drawable", getPackageName());
    }
    private void startRemoteServer() {
        try {
            pairingKey = new byte[32];
            new SecureRandom().nextBytes(pairingKey);
            remoteServer = new RemoteControlServer(8765, pairingKey, new RemoteControlServer.Handler() {
                @Override public void onCommand(JSONObject command) {
                    runOnUiThread(() -> handleRemoteCommand(command));
                }
                @Override public void onPeerCountChanged(int count) {
                    runOnUiThread(() -> {
                        if (pairingStatus != null) pairingStatus.setText(count > 0 ? "PHONE CONNECTED\nRemote + drawing active" : pairingDetails);
                    });
                }
            });
            remoteServer.start();
            String host = getLocalIpv4();
            if (host == null) {
                if (pairingStatus != null) pairingStatus.setText("Connect TV to Wi-Fi\nThen reopen pairing");
                return;
            }
            String key = Base64.getUrlEncoder().withoutPadding().encodeToString(pairingKey);
            String payload = "cosmicremote://pair?host=" + host + "&port=8765&key=" + key;
            Bitmap bitmap = makeQr(payload, 260);
            if (root != null) {
                View found = root.findViewWithTag("pairing-qr");
                if (found instanceof ImageView) ((ImageView) found).setImageBitmap(bitmap);
            }
            pairingDetails = "TV: " + host + "\nPort: 8765\nPairing key:\n" + key + "\nSame Wi-Fi only";
            if (pairingStatus != null) pairingStatus.setText(pairingDetails);
        } catch (Exception e) {
            if (pairingStatus != null) pairingStatus.setText("Remote unavailable\n" + e.getClass().getSimpleName());
        }
    }

    private Bitmap makeQr(String value, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
        return bitmap;
    }

    private String getLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void handleRemoteCommand(JSONObject command) {
        String type = command.optString("type", "");
        if ("key".equals(type)) {
            String key = command.optString("key", "");
            View focused = getCurrentFocus();
            if ("ok".equals(key) && focused != null) focused.performClick();
            else if ("back".equals(key)) {
                if (drawingBoard != null && drawingBoard.getVisibility() == View.VISIBLE) drawingBoard.setVisibility(View.GONE);
                else finish();
            } else if (focused != null) {
                int direction = "left".equals(key) ? View.FOCUS_LEFT : "right".equals(key) ? View.FOCUS_RIGHT :
                        "up".equals(key) ? View.FOCUS_UP : "down".equals(key) ? View.FOCUS_DOWN : 0;
                if (direction != 0) {
                    View next = focused.focusSearch(direction);
                    if (next != null) next.requestFocus();
                }
            }
        } else if ("pointer_delta".equals(type)) {
            float dx = (float) command.optDouble("dx", 0);
            float dy = (float) command.optDouble("dy", 0);
            remoteDxAccumulator += dx;
            remoteDyAccumulator += dy;
            if (Math.abs(remoteDxAccumulator) > 0.018f || Math.abs(remoteDyAccumulator) > 0.018f) {
                View focused = getCurrentFocus();
                int direction = Math.abs(remoteDxAccumulator) > Math.abs(remoteDyAccumulator)
                        ? (remoteDxAccumulator < 0 ? View.FOCUS_LEFT : View.FOCUS_RIGHT)
                        : (remoteDyAccumulator < 0 ? View.FOCUS_UP : View.FOCUS_DOWN);
                remoteDxAccumulator = 0f;
                remoteDyAccumulator = 0f;
                if (focused != null) {
                    View next = focused.focusSearch(direction);
                    if (next != null) next.requestFocus();
                }
            }
        } else if ("pointer_tap".equals(type)) {
            View focused = getCurrentFocus();
            if (focused != null) focused.performClick();
        } else if ("text_input".equals(type) || "voice_text".equals(type)) {
            String query = command.optString("text", "").trim().toLowerCase(java.util.Locale.ROOT);
            for (View tile : appTiles) {
                CharSequence label = tile.getContentDescription();
                tile.setVisibility(query.isEmpty() || (label != null && label.toString().toLowerCase(java.util.Locale.ROOT).contains(query)) ? View.VISIBLE : View.GONE);
            }
            if (grid != null) {
                View focused = getCurrentFocus();
                if (focused == null || focused.getVisibility() != View.VISIBLE) {
                    for (View tile : appTiles) if (tile.getVisibility() == View.VISIBLE) { tile.requestFocus(); break; }
                }
            }
        } else if ("stroke_begin".equals(type)) {
            if (drawingBoard != null) {
                drawingBoard.setVisibility(View.VISIBLE);
                drawingBoard.bringToFront();
                drawingBoard.beginStroke((float)command.optDouble("x", 0.5), (float)command.optDouble("y", 0.5),
                        command.optInt("color", Color.WHITE), (float)command.optDouble("width", 0.004));
            }
        } else if ("stroke_points".equals(type)) {
            if (drawingBoard != null) drawingBoard.addPoints(command.optJSONArray("points"));
        } else if ("stroke_end".equals(type)) {
            if (drawingBoard != null) drawingBoard.endStroke();
        } else if ("stroke_cancel".equals(type)) {
            if (drawingBoard != null) drawingBoard.clearDrawing();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WorkspaceWidgetLayer.REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (workspace != null) workspace.onImagePicked(data.getData());
        }
    }

    @Override protected void onDestroy() {
        if (remoteServer != null) remoteServer.closeSafely();
        super.onDestroy();
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

        FrameLayout planet = new FrameLayout(this);
        GradientDrawable orb = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8B9BFF, 0xFF5B4CB8, 0xFF222B63});
        orb.setShape(GradientDrawable.OVAL);
        orb.setStroke(dp(1), 0xFFB9C8FF);
        planet.setBackground(orb);

        // Actual installed-app icon sits inside a planet-like orb.
        ImageView appIcon = new ImageView(this);
        int themedIcon = themedIconForLabel(String.valueOf(info.loadLabel(getPackageManager())).toLowerCase(java.util.Locale.ROOT));
        if (themedIcon != 0) appIcon.setImageResource(themedIcon);
        else appIcon.setImageDrawable(info.loadIcon(getPackageManager()));
        appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
        planet.addView(appIcon, iconLp);

        // Tiny satellite marker on an orbital ring, drawn locally.
        View orbitDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFFFFE6A6);
        orbitDot.setBackground(dot);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP | Gravity.RIGHT);
        dotLp.topMargin = dp(2);
        dotLp.rightMargin = dp(8);
        planet.addView(orbitDot, dotLp);
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
