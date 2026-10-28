package io.causelane.cosmic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Lightweight, network-free cosmic background primitive for the Cosmic Universe TV prototype.
 *
 * Android 11 compatible: uses Canvas primitives only (no Android 13 runtime shaders, no video).
 * This is not a complete launcher or APK by itself. Host Activity should call setLowPowerMode()
 * when the TV is idle or when reduced motion is enabled.
 */
public final class CosmicSkyView extends View {
    private static final int STAR_COUNT = 105;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7319L);
    private final float[] stars = new float[STAR_COUNT * 3]; // normalized x, normalized y, radius
    private final RectF oval = new RectF();
    private long startTime = SystemClock.uptimeMillis();
    private boolean lowPowerMode;

    public CosmicSkyView(Context context) {
        super(context);
        setFocusable(false);
        setWillNotDraw(false);
        for (int i = 0; i < STAR_COUNT; i++) {
            stars[i * 3] = random.nextFloat();
            stars[i * 3 + 1] = random.nextFloat();
            stars[i * 3 + 2] = 0.5f + random.nextFloat() * 1.35f;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    public void setLowPowerMode(boolean enabled) {
        lowPowerMode = enabled;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Deep-space base; intentionally avoids expensive bitmap/video backgrounds.
        canvas.drawColor(0xFF050817);
        paint.setColor(0x332C2A70);
        canvas.drawCircle(w * 0.24f, h * 0.28f, Math.min(w, h) * 0.34f, paint);
        paint.setColor(0x22204C66);
        canvas.drawCircle(w * 0.78f, h * 0.70f, Math.min(w, h) * 0.30f, paint);
        paint.setColor(0x183C1E58);
        canvas.drawCircle(w * 0.58f, h * 0.20f, Math.min(w, h) * 0.23f, paint);

        final long elapsed = SystemClock.uptimeMillis() - startTime;
        for (int i = 0; i < STAR_COUNT; i++) {
            float twinkle = lowPowerMode ? 0.8f : 0.55f + 0.45f *
                    (float) Math.abs(Math.sin(elapsed / 850.0 + i * 1.73));
            int alpha = Math.min(255, Math.max(35, (int) (twinkle * 220)));
            paint.setColor((alpha << 24) | 0x00DCEBFF);
            float x = stars[i * 3] * w;
            float y = stars[i * 3 + 1] * h;
            canvas.drawCircle(x, y, stars[i * 3 + 2], paint);
        }

        // Orbital paths and a small planet motif in the upper-right quadrant.
        float cx = w * 0.79f;
        float cy = h * 0.30f;
        float radius = Math.min(w, h) * 0.12f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w / 1100f));
        paint.setColor(0x5577A9FF);
        oval.set(cx - radius * 1.75f, cy - radius * 0.58f,
                cx + radius * 1.75f, cy + radius * 0.58f);
        canvas.drawOval(oval, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF6C8DEB);
        canvas.drawCircle(cx, cy, radius * 0.52f, paint);
        paint.setColor(0xFF9CAFF8);
        canvas.drawCircle(cx - radius * 0.17f, cy - radius * 0.18f, radius * 0.13f, paint);

        // A restrained meteor streak that loops across the scene; no bitmaps or network assets.
        if (!lowPowerMode) {
            float period = 5200f;
            float p = (elapsed % period) / period;
            if (p < 0.22f) {
                float progress = p / 0.22f;
                float mx = w * (0.08f + progress * 0.70f);
                float my = h * (0.10f + progress * 0.34f);
                paint.setStrokeWidth(Math.max(1.2f, w / 800f));
                paint.setColor(0x99B9E8FF);
                canvas.drawLine(mx, my, mx - w * 0.055f, my - h * 0.035f, paint);
                paint.setColor(0xFFDCEBFF);
                canvas.drawCircle(mx, my, Math.max(1.5f, w / 900f), paint);
            }
            postInvalidateDelayed(33L);
        } else {
            postInvalidateDelayed(250L);
        }
    }
}
