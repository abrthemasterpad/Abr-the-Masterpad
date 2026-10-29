package io.causelane.cosmicremote;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;

/**
 * Native mobile control surface for the Cosmic Universe TV prototype.
 * No login, cloud relay, paid API or screen sharing. The TV must be running and on the same LAN.
 */
public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_VOICE = 2401;
    private EditText hostField, portField, keyField, textField;
    private TextView status;
    private LinearLayout root, panel;
    private RemoteControlClient client;
    private byte[] pairingKey;
    private int accent = 0xFF8CA8FF;
    private boolean manualPairingVisible = false;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF080D20);
        getWindow().setNavigationBarColor(0xFF080D20);
        buildScreen();
        String incoming = getIntent() == null ? null : getIntent().getDataString();
        if (incoming != null && incoming.startsWith("cosmicremote://pair")) applyPairingQr(incoming);
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF080D20);
        root.setPadding(dp(18), dp(14), dp(18), dp(12));

        TextView brand = new TextView(this);
        brand.setText("COSMIC REMOTE");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(22);
        brand.setLetterSpacing(0.12f);
        brand.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        root.addView(brand);

        TextView subtitle = new TextView(this);
        subtitle.setText("LOCAL CONTROL SURFACE  ·  NO ACCOUNT");
        subtitle.setTextColor(0xFF9DAED8);
        subtitle.setTextSize(10);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        status = new TextView(this);
        status.setText("Pair with the QR shown on your TV. Both devices must share the same network.");
        status.setTextColor(0xFFDCE6FF);
        status.setTextSize(12);
        root.addView(status);

        hostField = field("TV address (from QR)");
        portField = field("Port");
        portField.setText("8765");
        keyField = field("Pairing key (from QR)");
        LinearLayout manualFields = new LinearLayout(this);
        manualFields.setOrientation(LinearLayout.VERTICAL);
        manualFields.addView(hostField);
        manualFields.addView(portField);
        manualFields.addView(keyField);
        manualFields.setVisibility(View.GONE);
        root.addView(manualFields);
        Button manualPairing = button("ENTER PAIRING DETAILS MANUALLY");
        manualPairing.setOnClickListener(v -> {
            manualPairingVisible = !manualPairingVisible;
            manualFields.setVisibility(manualPairingVisible ? View.VISIBLE : View.GONE);
            manualPairing.setText(manualPairingVisible ? "HIDE MANUAL PAIRING" : "ENTER PAIRING DETAILS MANUALLY");
        });
        root.addView(manualPairing);

        LinearLayout connectRow = row();
        Button scan = button("SCAN TV QR");
        scan.setOnClickListener(v -> new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Scan the pairing QR shown on Cosmic Universe TV")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan());
        Button connect = button("CONNECT");
        connect.setOnClickListener(v -> connectManually());
        connectRow.addView(scan, weighted());
        connectRow.addView(connect, weighted());
        root.addView(connectRow);

        LinearLayout modes = row();
        addMode(modes, "REMOTE", "remote");
        addMode(modes, "PAD", "pad");
        addMode(modes, "DRAW", "draw");
        addMode(modes, "KEYS", "keys");
        addMode(modes, "VOICE", "voice");
        root.addView(modes);

        ScrollView scroller = new ScrollView(this);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(12), 0, dp(12));
        scroller.addView(panel);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button disconnect = button("DISCONNECT");
        disconnect.setOnClickListener(v -> disconnect());
        root.addView(disconnect);
        setContentView(root);
        showMode("remote");
    }

    private void addMode(LinearLayout row, String label, String mode) {
        Button b = button(label);
        b.setTextSize(10);
        b.setOnClickListener(v -> {
            if ("voice".equals(mode)) startVoice();
            else showMode(mode);
        });
        row.addView(b, weighted());
    }

    private void showMode(String mode) {
        panel.removeAllViews();
        if ("remote".equals(mode)) {
            TextView hint = hint("D-pad controls the launcher while it is open. OK activates the focused item.");
            panel.addView(hint);
            addKeyRow("UP", "up");
            LinearLayout row = row();
            row.addView(buttonAction("LEFT", "left"), weighted());
            row.addView(buttonAction("OK", "ok"), weighted());
            row.addView(buttonAction("RIGHT", "right"), weighted());
            panel.addView(row);
            addKeyRow("DOWN", "down");
            addKeyRow("BACK", "back");
        } else if ("pad".equals(mode)) {
            panel.addView(hint("Move one finger to navigate focus. Tap to activate the current focus. This prototype maps trackpad motion to D-pad focus steps."));
            TrackpadView trackpad = new TrackpadView(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 0, 1f);
            p.topMargin = dp(8);
            panel.addView(trackpad, p);
        } else if ("draw".equals(mode)) {
            panel.addView(hint("Draw here; incremental encrypted stroke points are sent to the TV canvas. Keep the TV launcher open."));
            DrawingPad drawing = new DrawingPad(this);
            panel.addView(drawing, new LinearLayout.LayoutParams(-1, 0, 1f));
            Button clear = button("CLEAR TV DRAWING");
            clear.setOnClickListener(v -> send("stroke_cancel"));
            panel.addView(clear);
        } else if ("keys".equals(mode)) {
            panel.addView(hint("Text is sent to the launcher to filter app labels. It does not type into arbitrary third-party apps."));
            textField = field("Search TV apps…");
            textField.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    JSONObject command = new JSONObject();
                    try { command.put("type", "text_input"); command.put("text", s.toString()); } catch (Exception ignored) { }
                    send(command);
                }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
            panel.addView(textField);
        } else {
            panel.addView(hint("Voice input uses Android's installed speech-recognition service. Offline availability depends on the device and language pack."));
            Button speak = button("START VOICE INPUT");
            speak.setOnClickListener(v -> startVoice());
            panel.addView(speak);
        }
    }

    private void addKeyRow(String label, String key) {
        Button b = button(label);
        b.setOnClickListener(v -> sendKey(key));
        panel.addView(b, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private Button buttonAction(String label, String key) {
        Button b = button(label);
        b.setOnClickListener(v -> sendKey(key));
        return b;
    }

    private void connectManually() {
        String host = hostField.getText().toString().trim();
        String port = portField.getText().toString().trim();
        String key = keyField.getText().toString().trim();
        if (host.isEmpty() || port.isEmpty() || key.isEmpty()) {
            status.setText("Scan the TV QR or enter host, port and key.");
            return;
        }
        try {
            pairingKey = Base64.getUrlDecoder().decode(key);
            if (pairingKey.length != 32) throw new IllegalArgumentException("Invalid key length");
            URI uri = new URI("ws://" + host + ":" + Integer.parseInt(port));
            if (client != null) { try { client.close(); } catch (Exception ignored) { } }
            client = new RemoteControlClient(uri, pairingKey, state -> runOnUiThread(() -> status.setText(state)));
            status.setText("Connecting over local network…");
            client.connect();
        } catch (Exception e) {
            status.setText("Pairing data invalid. Scan the current TV QR again.");
        }
    }

    private void applyPairingQr(String raw) {
        try {
            Uri uri = Uri.parse(raw);
            hostField.setText(uri.getQueryParameter("host"));
            portField.setText(uri.getQueryParameter("port"));
            keyField.setText(uri.getQueryParameter("key"));
            status.setText("TV found. Connecting securely over your local network…");
            connectManually();
        } catch (Exception e) {
            status.setText("This QR is not a supported Cosmic Remote pairing code.");
        }
    }

    private void startVoice() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a TV app name");
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (Exception e) {
            Toast.makeText(this, "No compatible speech-recognition service is available.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scan != null) {
            if (scan.getContents() != null) applyPairingQr(scan.getContents());
            return;
        }
        if (requestCode == REQUEST_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                JSONObject command = new JSONObject();
                try { command.put("type", "voice_text"); command.put("text", results.get(0)); } catch (Exception ignored) { }
                send(command);
                status.setText("Voice text sent to TV");
            }
        }
    }

    private void sendKey(String key) {
        JSONObject command = new JSONObject();
        try { command.put("type", "key"); command.put("key", key); } catch (Exception ignored) { }
        send(command);
    }

    private void send(String type) {
        JSONObject command = new JSONObject();
        try { command.put("type", type); } catch (Exception ignored) { }
        send(command);
    }

    private void send(JSONObject command) {
        if (client == null || !client.isOpen()) {
            status.setText("Not connected. Scan the TV QR and connect first.");
            return;
        }
        client.sendCommand(command);
    }

    private void disconnect() {
        if (client != null) { client.close(); client = null; }
        status.setText("Disconnected. TV pairing key rotates when the TV app restarts.");
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF8796BB);
        e.setTextSize(13);
        e.setPadding(dp(12), dp(4), dp(12), dp(4));
        e.setBackgroundColor(0xFF17213D);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.bottomMargin = dp(7);
        e.setLayoutParams(lp);
        return e;
    }

    private TextView hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFB8C6E7);
        t.setTextSize(12);
        t.setPadding(dp(3), dp(4), dp(3), dp(10));
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF27365F));
        b.setMinHeight(dp(44));
        return b;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        return l;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        return lp;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        if (client != null) client.close();
        super.onDestroy();
    }

    private final class TrackpadView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX, lastY, downX, downY;
        private boolean moved;
        TrackpadView(android.content.Context c) {
            super(c);
            setBackgroundColor(0xFF111A31);
            paint.setColor(0xFF8CA8FF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            setContentDescription("Touchpad for TV navigation");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRoundRect(dp(10), dp(10), getWidth()-dp(10), getHeight()-dp(10), dp(18), dp(18), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(14));
            canvas.drawText("TOUCHPAD", dp(20), getHeight()/2f, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if (e.getAction()==MotionEvent.ACTION_DOWN) { lastX=downX=x; lastY=downY=y; moved=false; return true; }
            if (e.getAction()==MotionEvent.ACTION_MOVE) {
                float dx=(x-lastX)/Math.max(1,getWidth());
                float dy=(y-lastY)/Math.max(1,getHeight());
                if (Math.abs(x-downX)+Math.abs(y-downY)>dp(8)) moved=true;
                if (moved) {
                    JSONObject c=new JSONObject();
                    try { c.put("type","pointer_delta"); c.put("dx",dx*1.5); c.put("dy",dy*1.5); } catch(Exception ignored) { }
                    send(c);
                }
                lastX=x; lastY=y; return true;
            }
            if (e.getAction()==MotionEvent.ACTION_UP) { if (!moved) send("pointer_tap"); performClick(); return true; }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }

    private final class DrawingPad extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path localPath = new Path();
        private float lastX,lastY;
        DrawingPad(android.content.Context c) {
            super(c);
            setBackgroundColor(0xFF111A31);
            paint.setColor(0xFFB7C8FF);
            paint.setStrokeWidth(dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            setContentDescription("Drawing pad mirrored to TV");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(localPath,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(11));
            canvas.drawText("DRAW — strokes stream to TV", dp(12), dp(22), paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=Math.max(0,Math.min(1f,e.getX()/Math.max(1,getWidth())));
            float y=Math.max(0,Math.min(1f,e.getY()/Math.max(1,getHeight())));
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                lastX=x; lastY=y; localPath.moveTo(e.getX(),e.getY());
                JSONObject c=new JSONObject();
                try { c.put("type","stroke_begin"); c.put("x",x); c.put("y",y); c.put("color",0xFFB7C8FF); c.put("width",0.004); } catch(Exception ignored) { }
                send(c); invalidate(); return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE) {
                JSONArray points=new JSONArray();
                JSONObject c=new JSONObject();
                try {
                    for (int i=0;i<e.getHistorySize();i++) {
                        points.put(Math.max(0,Math.min(1f,e.getHistoricalX(i)/Math.max(1,getWidth()))));
                        points.put(Math.max(0,Math.min(1f,e.getHistoricalY(i)/Math.max(1,getHeight()))));
                    }
                    points.put(x);
                    points.put(y);
                    c.put("type","stroke_points");
                    c.put("points",points);
                } catch (org.json.JSONException ignored) {
                    return true;
                }
                send(c); localPath.lineTo(e.getX(),e.getY()); lastX=x; lastY=y; invalidate(); return true;
            }
            if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                send("stroke_end"); return true;
            }
            return true;
        }
    }
}
