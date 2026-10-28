package io.causelane.cosmic;

import android.app.Activity;
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
    private GridLayout grid;
    private final List<ResolveInfo> launchables = new ArrayList<>();
    private int tileSize;
    private int columns = 5;
    private FrameLayout root;
    private RemoteDrawingView drawingBoard;
    private RemoteControlServer remoteServer;
    private TextView pairingStatus;
    private final List<View> appTiles = new ArrayList<>();
    private byte[] pairingKey;

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
        LinearLayout pairPanel = new LinearLayout(this);
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
        root.addView(pairPanel, pairLp);
        drawingBoard = new RemoteDrawingView(this);
        drawingBoard.setVisibility(View.GONE);
        FrameLayout.LayoutParams drawLp = new FrameLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.68f), (int)(getResources().getDisplayMetrics().heightPixels * 0.66f), Gravity.CENTER);
        root.addView(drawingBoard, drawLp);
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
                        if (pairingStatus != null) pairingStatus.setText(count > 0 ? "Phone connected\nRemote + drawing active" : "Scan to pair\nSame Wi-Fi · local only");
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
            if (pairingStatus != null) pairingStatus.setText("SCAN TO PAIR\nSame Wi-Fi\nNo account / cloud");
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
            if (Math.abs(dx) > 0.018f || Math.abs(dy) > 0.018f) {
                View focused = getCurrentFocus();
                int direction = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? View.FOCUS_LEFT : View.FOCUS_RIGHT) :
                        (dy < 0 ? View.FOCUS_UP : View.FOCUS_DOWN);
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
        appIcon.setImageDrawable(info.loadIcon(getPackageManager()));
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
