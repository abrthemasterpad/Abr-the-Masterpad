package io.causelane.cosmic;

import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.os.Build;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
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
    private TextView clock;
    private int noteCount = 0;
    private int todoCount = 0;
    private int imageCount = 0;
    private final java.util.ArrayList<String> undoStack = new java.util.ArrayList<>();
    private final java.util.ArrayList<String> redoStack = new java.util.ArrayList<>();
    private boolean applyingHistory = false;
    public static final int REQUEST_PICK_IMAGE = 7831;
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
            rememberUndo();
            addNoteCard(text, dp(245), dp(100) + noteCount * dp(20), true);
            noteCount++;
            save();
        }));
        toolbar.addView(addNote);
        Button addTodo = toolButton("+ Todo");
        addTodo.setOnClickListener(v -> promptText("New task", "", text -> {
            rememberUndo();
            addTodoCard(text, dp(510), dp(100) + todoCount * dp(20), false);
            todoCount++;
            save();
        }));
        toolbar.addView(addTodo);
        Button addImage = toolButton("+ Image");
        addImage.setOnClickListener(v -> {
            rememberUndo();
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (context instanceof Activity) ((Activity) context).startActivityForResult(pick, REQUEST_PICK_IMAGE);
        });
        toolbar.addView(addImage);
        Button theme = toolButton("Theme");
        theme.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Workspace theme")
                .setItems(new String[]{"Ocean glass", "Sunset", "Forest", "Bright paper"}, (d, which) -> {
                    int bg = new int[]{0xCC10172D,0xCC3D1E36,0xCC153C35,0xEEF5F7FA}[which];
                    toolbar.setBackground(panel(bg, 0x667C91D8));
                    clock.setBackground(panel(bg, 0x667C91D8));
                    clock.setTextColor(which == 3 ? 0xFF17344A : Color.WHITE);
                    prefs.edit().putInt("theme", which).apply();
                }).show());
        toolbar.addView(theme);
        Button analysis = toolButton("AI Analysis");
        analysis.setOnClickListener(v -> showLocalAnalysis());
        toolbar.addView(analysis);
        Button reminder = toolButton("Reminder");
        reminder.setOnClickListener(v -> promptText("Reminder delay in minutes", "15", delayText -> {
            try {
                int delay = Math.max(1, Math.min(10080, Integer.parseInt(delayText.trim())));
                promptText("Reminder message", "Check my tasks", message -> {
                    scheduleSystemReminder(message, delay);
                    addNoteCard("REMINDER IN " + delay + " MIN: " + message, dp(245), dp(160) + noteCount * dp(20), true);
                    noteCount++;
                });
            } catch (NumberFormatException e) { new AlertDialog.Builder(context).setMessage("Enter a delay from 1 to 10080 minutes.").setPositiveButton("OK", null).show(); }
        }));
        toolbar.addView(reminder);
        Button undo = toolButton("Undo");
        undo.setOnClickListener(v -> restoreHistory(undoStack, redoStack));
        toolbar.addView(undo);
        Button redo = toolButton("Redo");
        redo.setOnClickListener(v -> restoreHistory(redoStack, undoStack));
        toolbar.addView(redo);
        Button align = toolButton("Align");
        align.setOnClickListener(v -> alignCards());
        toolbar.addView(align);
        Button layers = toolButton("Layer");
        layers.setOnClickListener(v -> layerMenu());
        toolbar.addView(layers);
        Button daily = toolButton("Prayer + News");
        daily.setOnClickListener(v -> context.startActivity(new Intent(context, InfoReaderActivity.class)));
        toolbar.addView(daily);
        Button reset = toolButton("Reset layout");
        reset.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Reset workspace?")
                .setMessage("Remove the current notes and tasks from this TV?")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d, w) -> {
                    prefs.edit().remove("cards").apply();
                    for (int i = getChildCount() - 1; i >= 0; i--) {
                        View child = getChildAt(i);
                        if (child.getTag() instanceof JSONObject) removeViewAt(i);
                    }
                    noteCount = 0;
                    todoCount = 0;
                    save();
                }).show());
        toolbar.addView(reset);
        Button closeWorkspace = toolButton("Close workspace");
        closeWorkspace.setOnClickListener(v -> setVisibility(GONE));
        toolbar.addView(closeWorkspace);

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

    private void scheduleSystemReminder(String message, int delayMinutes) {\n        try {\n            Intent intent = new Intent(context, ReminderReceiver.class);\n            intent.putExtra("message", message);\n            int requestCode = (int)(System.currentTimeMillis() & 0x7fffffff);\n            PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);\n            long trigger = System.currentTimeMillis() + delayMinutes * 60000L;\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);\n            else alarms.set(AlarmManager.RTC_WAKEUP, trigger, pending);\n            new AlertDialog.Builder(context).setTitle("Reminder scheduled").setMessage("Android will schedule this reminder even if Nexora is closed. Delivery still depends on TV power and Android background restrictions.").setPositiveButton("OK", null).show();\n        } catch (Exception e) { new AlertDialog.Builder(context).setTitle("Reminder could not be scheduled").setMessage("Android rejected this alarm. Please try again.").setPositiveButton("OK", null).show(); }\n    }\n\n    private String snapshot() {\n        JSONArray arr = new JSONArray();\n        try {\n            for (int i=0; i<getChildCount(); i++) { View v = getChildAt(i); if (!(v.getTag() instanceof JSONObject)) continue;\n                JSONObject item = new JSONObject(((JSONObject)v.getTag()).toString()); item.put("x", (int)v.getX()); item.put("y", (int)v.getY()); item.put("width", v.getWidth()); arr.put(item); }\n        } catch (Exception ignored) { }\n        return arr.toString();\n    }\n    private void rememberUndo() { if (applyingHistory) return; undoStack.add(snapshot()); if (undoStack.size() > 30) undoStack.remove(0); redoStack.clear(); }\n    private void restoreHistory(java.util.ArrayList<String> from, java.util.ArrayList<String> to) {\n        if (from.isEmpty()) { new AlertDialog.Builder(context).setMessage("No more layout history.").setPositiveButton("OK", null).show(); return; }\n        to.add(snapshot()); String json = from.remove(from.size()-1);\n        applyingHistory = true;\n        for (int i=getChildCount()-1; i>=0; i--) if (getChildAt(i).getTag() instanceof JSONObject) removeViewAt(i);\n        noteCount=0; todoCount=0; imageCount=0;\n        try { JSONArray arr = new JSONArray(json); for (int i=0;i<arr.length();i++) { JSONObject o=arr.getJSONObject(i); int x=o.optInt("x",dp(240)), y=o.optInt("y",dp(110));\n            if ("todo".equals(o.optString("type"))) { addTodoCard(o.optString("text",""),x,y,o.optBoolean("done",false)); todoCount++; }\n            else if ("image".equals(o.optString("type"))) { addImageCard(o.optString("uri",""),x,y,false); imageCount++; }\n            else { addNoteCard(o.optString("text",""),x,y,false); noteCount++; } } } catch(Exception ignored) { }\n        applyingHistory = false; save();\n    }\n    private void alignCards() {\n        rememberUndo(); int left=dp(18), top=dp(96), gap=dp(18), col=0, row=0; int cardWidth=Math.max(dp(230),getWidth()/4);\n        for(int i=0;i<getChildCount();i++){ View v=getChildAt(i); if(!(v.getTag() instanceof JSONObject))continue;\n            int x=left+col*(cardWidth+gap); int y=top+row*dp(190); v.setX(x);v.setY(y); col++; if(col>=3){col=0;row++;} } save();\n    }\n    private void layerMenu() {\n        final View[] target = new View[]{getFocusedChild()};\n        new AlertDialog.Builder(context).setTitle("Selected card layer").setItems(new String[]{"Bring to front","Send to back"},(d,which)->{\n            View v=getCurrentFocus(); while(v!=null && !(v.getTag() instanceof JSONObject)) v=(View)v.getParent();\n            if(v==null){new AlertDialog.Builder(context).setMessage("Focus a note, task or image card first.").setPositiveButton("OK",null).show();return;}\n            rememberUndo(); if(which==0)v.bringToFront();else {removeView(v);addView(v,0);} save();\n        }).setNegativeButton("Cancel",null).show();\n    }\n    private void showLocalAnalysis() {
        int notes = 0, tasks = 0, done = 0, images = 0;
        StringBuilder pending = new StringBuilder();
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (!(view.getTag() instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) view.getTag();
            String type = item.optString("type");
            if ("note".equals(type)) notes++;
            else if ("image".equals(type)) images++;
            else if ("todo".equals(type)) {
                tasks++;
                if (item.optBoolean("done", false)) done++;
                else pending.append("• ").append(item.optString("text", "Task")).append("\\n");
            }
        }
        String summary = "Nexora local workspace analysis\\n\\nNotes: " + notes + "\\nImages: " + images
                + "\\nTasks: " + tasks + "\\nCompleted: " + done + "\\nOpen: " + (tasks - done)
                + "\\n\\nRecommended next action: " + (tasks > done ? "focus on one open task first." : "add a clear next task.")
                + (pending.length() > 0 ? "\\n\\nOpen tasks:\\n" + pending : "")
                + "\\n\\nThis analysis runs locally from your saved workspace. It is not a cloud LLM response."; 
        new AlertDialog.Builder(context).setTitle("AI Analysis").setMessage(summary).setPositiveButton("OK", null).show();
    }
    public void onImagePicked(Uri uri) {
        if (uri == null) return;
        try { context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        addImageCard(uri.toString(), dp(770), dp(115) + imageCount * dp(20), true);
        imageCount++;
    }

    private void addImageCard(String uriText, int x, int y, boolean persist) {
        LinearLayout card = cardShell(0xEE142039, 0xFF5E87D8);
        JSONObject model = new JSONObject();
        try { model.put("type", "image"); model.put("uri", uriText); } catch (Exception ignored) { }
        card.setTag(model);
        TextView title = label("IMAGE MAGNET", 10, 0xFFB6D4FF);
        title.setTypeface(Typeface.DEFAULT_BOLD); card.addView(title);
        ImageView image = new ImageView(context); image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try { image.setImageURI(Uri.parse(uriText)); } catch (Exception ignored) { }
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(125)));
        LinearLayout actions = new LinearLayout(context); actions.setGravity(Gravity.RIGHT);
        Button resize = tinyButton("Resize");
        resize.setOnClickListener(v -> { rememberUndo(); ViewGroup.LayoutParams lp = card.getLayoutParams(); lp.width = lp.width < dp(300) ? dp(340) : lp.width < dp(400) ? dp(430) : dp(230); card.setLayoutParams(lp); save(); });
        Button delete = tinyButton("×"); delete.setOnClickListener(v -> { removeView(card); save(); });
        actions.addView(resize); actions.addView(delete); card.addView(actions);
        placeCard(card, x, y); if (persist) save();
    }
    private void addNoteCard(String text, int x, int y, boolean persist) {
        LinearLayout card = cardShell(0xFFEADFA8, 0xFF6F6335);
        JSONObject model = new JSONObject(); try { model.put("type", "note"); model.put("text", text); } catch (Exception ignored) { } card.setTag(model);
        TextView title = label("✦ NOTE", 10, 0xFF6D5A27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView body = label(text, 15, 0xFF25283A);
        body.setPadding(0, dp(7), 0, 0);
        card.addView(body);
        Button edit = tinyButton("Edit");
        edit.setOnClickListener(v -> promptText("Edit note", body.getText().toString(), updated -> {
            rememberUndo();
            body.setText(updated); try { ((JSONObject)card.getTag()).put("text", updated); } catch (Exception ignored) { } save();
        }));
        Button delete = tinyButton("×");
        delete.setOnClickListener(v -> { removeView(card); save(); });
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        actions.addView(edit); addResizeAction(card, actions); actions.addView(delete);
        card.addView(actions);
        placeCard(card, x, y);
        if (persist) save();
    }

    private void addTodoCard(String text, int x, int y, boolean done) {
        LinearLayout card = cardShell(0xE51B2945, 0xFF6179B9);
        JSONObject model = new JSONObject(); try { model.put("type", "todo"); model.put("text", text); model.put("done", done); } catch (Exception ignored) { } card.setTag(model);
        TextView title = label("✓ TASK", 10, 0xFF9EB5FF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        Button toggle = tinyButton((done ? "✓  " : "○  ") + text);
        toggle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        toggle.setOnClickListener(v -> {
            rememberUndo();
            boolean checked = !toggle.getText().toString().startsWith("✓");
            toggle.setText((checked ? "✓  " : "○  ") + text);
            toggle.setAlpha(checked ? 0.55f : 1f);
            try { ((JSONObject) card.getTag()).put("done", checked); } catch (Exception ignored) { }
            save();
        });
        card.addView(toggle);
        Button edit = tinyButton("Edit");
        edit.setOnClickListener(v -> promptText("Edit task", text, updated -> {
            rememberUndo();
            toggle.setText((toggle.getText().toString().startsWith("✓") ? "✓  " : "○  ") + updated);
            try { ((JSONObject) card.getTag()).put("text", updated); } catch (Exception ignored) { }
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

    private void addResizeAction(View card, LinearLayout actions) {
        Button resize = tinyButton("Size");
        resize.setOnClickListener(v -> {
            rememberUndo();
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            int current = lp.width;
            lp.width = current < dp(270) ? dp(300) : current < dp(350) ? dp(380) : dp(230);
            card.setLayoutParams(lp); save();
        });
        actions.addView(resize);
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
                    rememberUndo();
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
                    moving = null;
                    float snap = dp(16);
                    v.setX(Math.max(0, Math.round(v.getX() / snap) * snap));
                    v.setY(Math.max(dp(58), Math.round(v.getY() / snap) * snap));
                    save(); return true;
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
                } else if ("image".equals(o.optString("type"))) {
                    addImageCard(o.optString("uri", ""), x, y, false); imageCount++;
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
                o.put("x", (int)v.getX()); o.put("y", (int)v.getY()); o.put("width", v.getWidth());
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
