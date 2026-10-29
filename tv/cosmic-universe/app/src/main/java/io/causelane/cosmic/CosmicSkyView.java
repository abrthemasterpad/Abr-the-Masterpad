package io.causelane.cosmic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Lightweight, network-free living wallpaper for Android 11+.
 * Canvas-only rendering keeps it usable on modest TV hardware.
 */
public final class CosmicSkyView extends View {
    private static final int STAR_COUNT = 105;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7319L);
    private final float[] stars = new float[STAR_COUNT * 3];
    private final RectF oval = new RectF();
    private final RectF eye = new RectF();
    private long startTime = SystemClock.uptimeMillis();
    private boolean lowPowerMode;
    private float gazeX = 0.5f;
    private float gazeY = 0.5f;
    private float targetGazeX = 0.5f;
    private float targetGazeY = 0.5f;

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

    /** Receives the focused control's normalized screen position from the host launcher. */
    public void setGazeTarget(float normalizedX, float normalizedY) {
        targetGazeX = Math.max(0f, Math.min(1f, normalizedX));
        targetGazeY = Math.max(0f, Math.min(1f, normalizedY));
        if (!lowPowerMode) invalidate();
    }

    public void setLowPowerMode(boolean enabled) {
        lowPowerMode = enabled;
        invalidate();
    }

    private void glow(Canvas canvas, float x, float y, float radius, int color) {
        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

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
            canvas.drawCircle(stars[i * 3] * w, stars[i * 3 + 1] * h, stars[i * 3 + 2], paint);
        }

        // Soft orbital nebula and planet.
        float cx = w * 0.79f, cy = h * 0.30f;
        float radius = Math.min(w, h) * 0.12f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w / 1100f));
        paint.setColor(0x5577A9FF);
        oval.set(cx - radius * 1.75f, cy - radius * 0.58f, cx + radius * 1.75f, cy + radius * 0.58f);
        canvas.drawOval(oval, paint);
        paint.setStyle(Paint.Style.FILL);
        glow(canvas, cx, cy, radius * 0.52f, 0xFF6C8DEB);
        glow(canvas, cx - radius * 0.17f, cy - radius * 0.18f, radius * 0.13f, 0xFF9CAFF8);

        // Abstract living eyes: pupils smoothly follow the focused remote-control target.
        float smooth = lowPowerMode ? 0.08f : 0.18f;
        gazeX += (targetGazeX - gazeX) * smooth;
        gazeY += (targetGazeY - gazeY) * smooth;
        float eyeCenterX = w * 0.50f;
        float eyeCenterY = h * 0.43f;
        float eyeGap = w * 0.065f;
        float eyeRx = w * 0.035f;
        float eyeRy = h * 0.020f;
        float pupilRangeX = eyeRx * 0.46f;
        float pupilRangeY = eyeRy * 0.40f;
        float lookX = (gazeX - 0.5f) * 2f;
        float lookY = (gazeY - 0.5f) * 2f;
        float pulse = lowPowerMode ? 0.75f : 0.82f + 0.18f * (float)Math.sin(elapsed / 730.0);
        // A short periodic blink, with both eyes closing together like a single abstract entity.
        float blinkPhase = elapsed % 4870L;
        float blink = (blinkPhase > 4510f && blinkPhase < 4620f)
                ? Math.max(0.07f, Math.abs((blinkPhase - 4565f) / 55f)) : 1f;
        eyeRy *= blink;

        for (int side = -1; side <= 1; side += 2) {
            float ex = eyeCenterX + side * eyeGap;
            // Wide, dark socket with a restrained cyan-violet aura.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x3321C8FF);
            canvas.drawOval(ex - eyeRx * 1.75f, eyeCenterY - eyeRy * 2.3f,
                    ex + eyeRx * 1.75f, eyeCenterY + eyeRy * 2.3f, paint);
            paint.setColor(0xD90A1028);
            canvas.drawOval(ex - eyeRx, eyeCenterY - eyeRy, ex + eyeRx, eyeCenterY + eyeRy, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, w / 1000f));
            paint.setColor(0xAA70E8FF);
            canvas.drawOval(ex - eyeRx, eyeCenterY - eyeRy, ex + eyeRx, eyeCenterY + eyeRy, paint);
            paint.setStyle(Paint.Style.FILL);
            float px = ex + lookX * pupilRangeX;
            float py = eyeCenterY + lookY * pupilRangeY;
            float pupilR = Math.max(2f, Math.min(eyeRx, eyeRy) * 0.66f) * pulse;
            glow(canvas, px, py, pupilR * 2.4f, 0x5524DFFF);
            glow(canvas, px, py, pupilR, 0xFF64E9FF);
            glow(canvas, px - pupilR * 0.24f, py - pupilR * 0.25f, pupilR * 0.28f, 0xFFF2FCFF);
        }

        // Tiny moving motes give the eyes a living field without video or network assets.
        if (!lowPowerMode) {
            for (int i = 0; i < 8; i++) {
                float phase = (elapsed / (1900f + i * 95f) + i * 0.137f) % 1f;
                float mx = eyeCenterX + (float)Math.sin(elapsed / 1300.0 + i) * w * (0.08f + i * 0.006f);
                float my = eyeCenterY + (phase - 0.5f) * h * 0.22f;
                glow(canvas, mx, my, Math.max(1f, w / 1500f), 0x9973CFFF);
            }
            float period = 5200f;
            float p = (elapsed % period) / period;
            if (p < 0.22f) {
                float progress = p / 0.22f;
                float mx = w * (0.08f + progress * 0.70f);
                float my = h * (0.10f + progress * 0.34f);
                paint.setStrokeWidth(Math.max(1.2f, w / 800f));
                paint.setColor(0x99B9E8FF);
                canvas.drawLine(mx, my, mx - w * 0.055f, my - h * 0.035f, paint);
                glow(canvas, mx, my, Math.max(1.5f, w / 900f), 0xFFDCEBFF);
            }
            postInvalidateDelayed(33L);
        } else {
            postInvalidateDelayed(250L);
        }
    }
}
