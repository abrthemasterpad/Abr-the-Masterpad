package io.causelane.cosmic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Lightweight TV-side live drawing surface. Coordinates are normalized to [0,1]. */
public final class RemoteDrawingView extends View {
    private static final class Stroke {
        final Path path = new Path();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Stroke(int color, float width) {
            paint.setColor(color);
            paint.setStrokeWidth(width);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
    }
    private final Paint board = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke active;
    private float lastX, lastY;

    public RemoteDrawingView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        board.setColor(0xE91B2440);
        border.setColor(0xFF8CA8FF);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3f);
        setContentDescription("Live mobile drawing pad");
    }

    public void beginStroke(float x, float y, int color, float normalizedWidth) {
        float px = clamp(x) * Math.max(1, getWidth());
        float py = clamp(y) * Math.max(1, getHeight());
        active = new Stroke(color, Math.max(2f, normalizedWidth * Math.min(getWidth(), getHeight())));
        active.path.moveTo(px, py);
        lastX = px; lastY = py;
        strokes.add(active);
        invalidate();
    }

    public void addPoints(JSONArray points) {
        if (active == null || points == null) return;
        for (int i = 0; i + 1 < points.length(); i += 2) {
            float x = clamp((float) points.optDouble(i, 0)) * Math.max(1, getWidth());
            float y = clamp((float) points.optDouble(i + 1, 0)) * Math.max(1, getHeight());
            // Quadratic interpolation softens coalesced samples without allocating a bitmap per move.
            float mx = (lastX + x) * 0.5f;
            float my = (lastY + y) * 0.5f;
            active.path.quadTo(lastX, lastY, mx, my);
            lastX = x; lastY = y;
        }
        invalidate();
    }

    public void endStroke() { active = null; }
    public void clearDrawing() { strokes.clear(); active = null; invalidate(); }
    private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = 3f;
        RectF r = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawRoundRect(r, 28f, 28f, board);
        canvas.drawRoundRect(r, 28f, 28f, border);
        for (Stroke s : strokes) canvas.drawPath(s.path, s.paint);
    }
}
