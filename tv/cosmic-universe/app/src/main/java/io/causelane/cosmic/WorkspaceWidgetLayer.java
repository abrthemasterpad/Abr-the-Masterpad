package io.causelane.cosmic;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local-first starter workspace: persistent clock, sticky notes and checklist cards.
 * Cards are movable by touch/trackpad drag and can be repositioned with the TV D-pad
 * while selected. This is an initial widget engine, not the final full canvas editor.
 */
public final class WorkspaceWidgetLayer extends FrameLayout {
    private final Context context;
    private final SharedPreferences prefs;
    private final LinearLayout cards;
    private final TextView clock;
    private int noteCount = 0;
    private int todoCount = 0;
    private float downX, downY, startX, startY;
    private View moving;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
                    + "\n" + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(now));
            postDelayed(this, 15000);
        }
    };

    public WorkspaceWidgetLayer(Context context) {
        super(context);
        this.context = context;
        prefs = context.getSharedPreferences("cosmic_workspace_v1", Context.MODE_PRIVATE);
        setClipChildren(false);
        setClipToPadding(false);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));
        toolbar.setBackground(panel(0xCC10172D, 0x557C91D8));
        addView(toolbar, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));

        TextView brand = label("WORKSPACE", 10, 0xFF9EB5FF);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        toolbar.addView(brand, new LinearLayout.LayoutParams(-2, -2));
        Button addNote = toolButton("+ Note");
        addNote.setOnClickListener(v -> promptText("New sticky note", "", text -> {
            addNoteCard(text, dp(245), dp(100) + noteCount * dp(20), true);
            noteCount++;
            save();
        }));
        toolbar.addView(addNote);
        Button addTodo = toolButton("+ Todo");
        addTodo.setOnClickListener(v -> promptText("New task", "", text -> {
            addTodoCard(text, dp(510), dp(100) + todoCount * dp(20), false);
            todoCount++;
            save();
        }));
        toolbar.addView(addTodo);
        Button reset = toolButton("Reset layout");
        reset.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Reset workspace?")
                .setMessage("Remove the current notes and tasks from this TV?")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d, w) -> {
                    prefs.edit().remove("cards").apply();
                    removeViews();
                    recreateWorkspace();
                }).show());
        toolbar.addView(reset);

        clock = label("", 20, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setPadding(dp(15), dp(12), dp(15), dp(12));
        clock.setBackground(panel(0xCC171F39, 0x667C91D8));
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(dp(245), dp(86), Gravity.TOP | Gravity.RIGHT);
        clockLp.setMargins(0, dp(12), dp(22), 0);
        addView(clock, clockLp);
        clockTick.run();

        cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setVisibility(GONE);
        addView(cards, new FrameLayout.LayoutParams(1, 1));
        restore();
    }

    private void recreateWorkspace() {
        // Keep the toolbar and clock, rebuild the card container and reset saved layout.
        removeView(cards);
        // The container is intentionally recreated in place; controls remain available.
        cards.removeAllViews();
        noteCount = 0;
        todoCount = 0;
        save();
    }

    private interface TextResult { void accept(String value); }
    private void promptText(String title, String initial, TextResult result) {
        EditText input = new EditText(context);
        input.setText(initial);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF9DA9C7);
        input.setHint("Type here…");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setBackground(panel(0xFF1A2340, 0xFF6679B5));
        new AlertDialog.Builder(context).setTitle(title).setView(input)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) result.accept(value);
                }).show();
    }

    private void addNoteCard(String text, int x, int y, boolean persist) {
        LinearLayout card = cardShell(0xFFEADFA8, 0xFF6F6335);\n        JSONObject model = new JSONObject(); try { model.put("type", "note"); model.put("text", text); } catch (Exception ignored) { } card.setTag(model);
        TextView title = label("✦ NOTE", 10, 0xFF6D5A27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView body = label(text, 15, 0xFF25283A);
        body.setPadding(0, dp(7), 0, 0);
        card.addView(body);
        Button edit = tinyButton("Edit");
        edit.setOnClickListener(v -> promptText("Edit note", body.getText().toString(), updated -> {
            body.setText(updated); try { ((JSONObject)card.getTag()).put("text", updated); } catch (Exception ignored) { } save();
        }));
        Button delete = tinyButton("×");
        delete.setOnClickListener(v -> { removeView(card); save(); });
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        actions.addView(edit); actions.addView(delete);
        card.addView(actions);
        placeCard(card, x, y);
        if (persist) save();
    }

    private void addTodoCard(String text, int x, int y, boolean done) {
        LinearLayout card = cardShell(0xE51B2945, 0xFF6179B9);\n        JSONObject model = new JSONObject(); try { model.put("type", "todo"); model.put("text", text); model.put("done", done); } catch (Exception ignored) { } card.setTag(model);
        TextView title = label("✓ TASK", 10, 0xFF9EB5FF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        Button toggle = tinyButton((done ? "✓  " : "○  ") + text);
        toggle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        toggle.setOnClickListener(v -> {
            boolean checked = !toggle.getText().toString().startsWith("✓");
            toggle.setText((checked ? "✓  " : "○  ") + text);
            toggle.setAlpha(checked ? 0.55f : 1f);
            save();
        });
        card.addView(toggle);
        Button edit = tinyButton("Edit");
        edit.setOnClickListener(v -> promptText("Edit task", text, updated -> {
            toggle.setText((toggle.getText().toString().startsWith("✓") ? "✓  " : "○  ") + updated);
            save();
        }));
        Button delete = tinyButton("×");
        delete.setOnClickListener(v -> { removeView(card); save(); });
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        actions.addView(edit); actions.addView(delete);
        card.addView(actions);
        placeCard(card, x, y);
        if (done) toggle.setAlpha(0.55f);
    }

    private LinearLayout cardShell(int color, int stroke) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(10), dp(10), dp(8));
        card.setBackground(panel(color, stroke));
        card.setFocusable(true);
        card.setElevation(dp(8));
        card.setOnFocusChangeListener((v, focused) -> {
            v.setScaleX(focused ? 1.025f : 1f);
            v.setScaleY(focused ? 1.025f : 1f);
        });
        card.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    moving = v; downX = event.getRawX(); downY = event.getRawY();
                    startX = v.getX(); startY = v.getY(); return true;
                case MotionEvent.ACTION_MOVE:
                    if (moving == v) {
                        float nx = Math.max(0, Math.min(getWidth() - v.getWidth(), startX + event.getRawX() - downX));
                        float ny = Math.max(dp(58), Math.min(getHeight() - v.getHeight(), startY + event.getRawY() - downY));
                        v.setX(nx); v.setY(ny);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    moving = null; save(); return true;
            }
            return true;
        });
        card.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            float step = dp(12);
            if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT) v.setX(Math.max(0, v.getX()-step));
            else if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) v.setX(Math.min(getWidth()-v.getWidth(), v.getX()+step));
            else if (key == android.view.KeyEvent.KEYCODE_DPAD_UP) v.setY(Math.max(dp(58), v.getY()-step));
            else if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) v.setY(Math.min(getHeight()-v.getHeight(), v.getY()+step));
            else return false;
            save(); return true;
        });
        return card;
    }

    private void placeCard(View card, int x, int y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(230), -2);
        lp.leftMargin = Math.max(0, x); lp.topMargin = Math.max(dp(58), y);
        addView(card, lp);
        card.setFocusable(true);
    }

    private void restore() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("cards", "[]"));
            for (int i=0; i<arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int x = o.optInt("x", dp(240)), y = o.optInt("y", dp(110));
                if ("todo".equals(o.optString("type"))) {
                    addTodoCard(o.optString("text", ""), x, y, o.optBoolean("done", false)); todoCount++;
                } else {
                    addNoteCard(o.optString("text", ""), x, y, false); noteCount++;
                }
            }
        } catch (Exception ignored) { }
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (int i=0; i<getChildCount(); i++) {
                View v = getChildAt(i);
                if (!(v instanceof LinearLayout) || v == cards || v.getTag() == null) continue;
                JSONObject o = (JSONObject)v.getTag();
                o.put("x", (int)v.getX()); o.put("y", (int)v.getY());
                arr.put(o);
            }
            // Card tags are the canonical, versioned local workspace model.
            prefs.edit().putString("cards", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button toolButton(String text) {
        Button b = new Button(context); b.setText(text); b.setTextSize(10);
        b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackground(panel(0xFF202B4A, 0x667C91D8));
        b.setFocusable(true); return b;
    }

    private Button tinyButton(String text) {
        Button b = new Button(context); b.setText(text); b.setTextSize(10);
        b.setAllCaps(false); b.setTextColor(0xFFF1F4FF);
        b.setMinHeight(dp(28)); b.setMinimumHeight(dp(28));
        b.setBackground(panel(0x55202B4A, 0x557C91D8));
        return b;
    }

    private GradientDrawable panel(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(13)); d.setStroke(dp(1), stroke); return d;
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
