package com.stash.feature.nowplaying.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.Arrays;

/**
 * Production adapter for the approved Optical Native Lab V2 emission technique.
 * Isolated from playback and artwork analysis. Receives only a normalized gradient.
 * Uses a small software emission texture composited by a hardware-accelerated View.
 */
public final class SonoraOpticalTimelineView extends View {
    public interface OnSeek { void seek(float fraction); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final float density;
    private Bitmap texture;
    private int[] colors = {0xffa13ee6, 0xffbd74f7};
    private float[] stops = {0f, 1f};
    private int inactive = 0xff25202b;
    private float fraction;
    private boolean dirty = true;
    private boolean dragging;
    private OnSeek onSeek;
    private static final int RELIEF_CELLS = 192;
    private final float[] relief = new float[RELIEF_CELLS];
    private float phase, agitation, motionDrift;
    private float motionFlow = .72f, motionElastic = .20f, motionTense = .08f;
    private boolean motionEnabled = true;

    public SonoraOpticalTimelineView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void configure(int[] newColors, float[] newStops, int newInactive) {
        if (newColors == null || newStops == null || newColors.length < 2 ||
            newColors.length != newStops.length) return;
        if (!Arrays.equals(colors, newColors) || !Arrays.equals(stops, newStops) ||
            inactive != newInactive) {
            colors = newColors.clone();
            stops = newStops.clone();
            inactive = newInactive;
            dirty = true;
            invalidate();
        }
    }

    public void setFraction(float value) {
        if (!Float.isFinite(value)) return;
        float next = Math.max(0f, Math.min(1f, value));
        if (Math.abs(next - fraction) > 0.0001f) {
            fraction = next;
            dirty = true;
            invalidate();
        }
    }

    public void setOnSeek(OnSeek callback) { onSeek = callback; }

    /** Visual state only: no touching the playback pipeline or PCM buffers. */
    public void setMotionEnabled(boolean enabled) {
        if (motionEnabled != enabled) { motionEnabled = enabled; invalidate(); }
    }

    public void updateMotion(float flow, float elastic, float tense, float attack,
                             float evolution, boolean valid, float seconds) {
        if (!motionEnabled || !isAttachedToWindow()) return;
        float dt = Math.max(0f, Math.min(.05f, seconds));
        if (dt <= 0f) return;
        float response = 1f - (float)Math.exp(-dt / 1.8f);
        motionFlow += ((valid ? flow : .72f) - motionFlow) * response;
        motionElastic += ((valid ? elastic : .20f) - motionElastic) * response;
        motionTense += ((valid ? tense : .08f) - motionTense) * response;
        float target = valid ? Math.max(0f, Math.min(1f, attack * .58f + evolution * .42f)) : 0f;
        agitation += (target - agitation) * (1f - (float)Math.exp(-dt / .36f));
        phase += dt * (.47f + motionElastic * .24f + motionTense * .31f);
        motionDrift = (float)Math.sin(phase * .43f + Math.sin(phase * .17f) * .9f) * .58f;
        if (valid && fraction > 0f) {
            int index = Math.max(0, Math.min(RELIEF_CELLS - 1, Math.round(fraction * (RELIEF_CELLS - 1))));
            float value = Math.max(0f, Math.min(1f, attack * .75f + evolution * .35f));
            float retention = 1f - (float)Math.exp(-dt / .24f);
            for (int i = Math.max(0, index - 1); i <= Math.min(RELIEF_CELLS - 1, index + 1); i++) {
                float influence = i == index ? 1f : .4f;
                relief[i] += (value * influence - relief[i]) * retention;
            }
        }
        // Repaint contours without regenerating the cached optical bitmap.
        invalidate();
    }

    private float reliefAt(float position) {
        float f = Math.max(0f, Math.min(RELIEF_CELLS - 1f, position * (RELIEF_CELLS - 1)));
        int first = (int)f;
        int second = Math.min(RELIEF_CELLS - 1, first + 1);
        return relief[first] + (relief[second] - relief[first]) * (f - first);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        dirty = true;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (texture != null) { texture.recycle(); texture = null; }
    }

    private float dp(float value) { return value * density; }

    private void reset() {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
    }

    private static int alpha(int color, int opacity) {
        return (color & 0x00ffffff) | (Math.max(0, Math.min(255, opacity)) << 24);
    }

    private int colorAt(float at) {
        at = Math.max(0f, Math.min(1f, at));
        for (int i = 1; i < stops.length; i++) {
            if (at <= stops[i]) {
                float span = stops[i] - stops[i - 1];
                float t = span > 0f ? (at - stops[i - 1]) / span : 1f;
                int a = colors[i - 1], b = colors[i];
                return Color.rgb(
                    Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                    Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                    Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
            }
        }
        return colors[colors.length - 1];
    }

    /** Stops are cut at the playhead, not clipRect'd: no hard rectangular glow edge. */
    private Shader activeGradient(float start, float tip, float y, float strength) {
        int count = 1;
        for (int i = 1; i < stops.length; i++) if (stops[i] < fraction) count++;
        int[] stopsColors = new int[count + 1];
        float[] positions = new float[count + 1];
        stopsColors[0] = alpha(colors[0], Math.round(strength * 255f));
        positions[0] = 0f;
        int cursor = 1;
        for (int i = 1; i < stops.length; i++) {
            if (stops[i] >= fraction) break;
            stopsColors[cursor] = alpha(colors[i], Math.round(strength * 255f));
            positions[cursor] = stops[i] / Math.max(.00001f, fraction);
            cursor++;
        }
        stopsColors[cursor] = alpha(colorAt(fraction), Math.round(strength * 255f));
        positions[cursor] = 1f;
        return new LinearGradient(start, y, Math.max(start + .5f, tip), y,
            Arrays.copyOf(stopsColors, cursor + 1),
            Arrays.copyOf(positions, cursor + 1), Shader.TileMode.CLAMP);
    }

    private void emission(Canvas output, float x0, float tip, float y,
                          float width, float blur, float alpha) {
        reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setShader(activeGradient(x0, tip, y, alpha));
        paint.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        output.drawLine(x0, y, tip, y, paint);
    }

    private void rebuild() {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (texture == null || texture.getWidth() != width || texture.getHeight() != height) {
            if (texture != null) texture.recycle();
            texture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        texture.eraseColor(Color.TRANSPARENT);
        Canvas off = new Canvas(texture);
        float x0 = dp(17f), x1 = width - dp(17f), y = height * .5f;
        float tip = x0 + (x1 - x0) * fraction;
        float coreRadius = dp(5.8f);
        int local = colorAt(fraction);

        reset();
        paint.setColor(alpha(inactive, 75));
        paint.setStrokeWidth(dp(1.9f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        off.drawLine(tip, y, x1, y, paint);
        if (fraction > 0.001f) {
            // Fixed 100% optical emission. Three independent blur scales.
            emission(off, x0, tip, y, dp(4.8f), dp(8.2f), .31f);
            emission(off, x0, tip, y, dp(3.5f), dp(4.2f), .42f);
            emission(off, x0, tip, y, dp(2.8f), dp(1.9f), .53f);

            reset();
            paint.setShader(new RadialGradient(tip, y, dp(13f),
                new int[]{alpha(local, 114), alpha(local, 42), Color.TRANSPARENT},
                new float[]{0f, .42f, 1f}, Shader.TileMode.CLAMP));
            off.drawCircle(tip, y, dp(13f), paint);

            reset();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(2.3f));
            paint.setShader(activeGradient(x0, tip, y, 1f));
            off.drawLine(x0, y, tip, y, paint);

            float whiteStart = Math.max(x0, tip - dp(22f));
            reset();
            paint.setStrokeWidth(dp(.72f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setShader(new LinearGradient(whiteStart, y, tip, y,
                new int[]{0x00ffffff, 0x38ffffff, 0x53ffffff},
                new float[]{0f, .7f, 1f}, Shader.TileMode.CLAMP));
            off.drawLine(whiteStart, y, tip, y, paint);
        }

        reset();
        paint.setColor(alpha(local, 100));
        paint.setMaskFilter(new BlurMaskFilter(dp(2.6f), BlurMaskFilter.Blur.NORMAL));
        off.drawCircle(tip, y, coreRadius + dp(1f), paint);

        reset();
        paint.setColor(local);
        off.drawCircle(tip, y, coreRadius, paint);
        dirty = false;
    }

    /**
     * Draw raised optical contours in the SAME Android View as the rail.
     * The future track is untouched and both contours terminate at the thumb.
     */
    private void drawRaisedTrail(Canvas canvas) {
        float x0 = dp(17f), x1 = getWidth() - dp(17f), y = getHeight() * .5f;
        float tip = x0 + (x1 - x0) * fraction;
        if (tip - x0 < dp(3f)) return;
        int steps = Math.max(8, Math.min(192, (int)((tip - x0) / dp(2f))));
        Path upper = new Path(), lower = new Path(), body = new Path();
        for (int i = 0; i <= steps; i++) {
            float x = x0 + (tip - x0) * i / steps;
            float t = (x - x0) / Math.max(1f, x1 - x0);
            float vibration = (float)(Math.sin(t * 42f + phase * .40f) * .41f +
                Math.sin(t * 91f - phase * .63f + motionDrift) * .20f);
            float history = reliefAt(t);
            float thick = Math.max(1.4f, Math.min(4.8f,
                2.2f + history * 2f + motionElastic * .38f + motionTense * .25f));
            float edge = Math.max(0f, Math.min(1f,
                Math.min((x - x0) / dp(3f), (tip - x) / dp(3f))));
            float top = y - (thick + vibration * (1f + history * .6f)) * dp(1f) * edge;
            float bottom = y + (thick * .72f - vibration * .45f) * dp(1f) * edge;
            if (i == 0) {
                upper.moveTo(x, top);
                lower.moveTo(x, bottom);
                body.moveTo(x, top);
            } else {
                upper.lineTo(x, top);
                lower.lineTo(x, bottom);
                body.lineTo(x, top);
            }
        }
        for (int i = steps; i >= 0; i--) {
            float x = x0 + (tip - x0) * i / steps;
            float t = (x - x0) / Math.max(1f, x1 - x0);
            float vibration = (float)(Math.sin(t * 42f + phase * .40f) * .41f +
                Math.sin(t * 91f - phase * .63f + motionDrift) * .20f);
            float history = reliefAt(t);
            float thick = Math.max(1.4f, Math.min(4.8f,
                2.2f + history * 2f + motionElastic * .38f + motionTense * .25f));
            float edge = Math.max(0f, Math.min(1f,
                Math.min((x - x0) / dp(3f), (tip - x) / dp(3f))));
            body.lineTo(x, y + (thick * .72f - vibration * .45f) * dp(1f) * edge);
        }
        body.close();
        reset();
        paint.setShader(new LinearGradient(0f, y - dp(5f), 0f, y + dp(5f),
            new int[]{alpha(colorAt(fraction), 18), alpha(colorAt(fraction), 100),
                alpha(colorAt(fraction), 56), alpha(colorAt(fraction), 5)},
            new float[]{0f, .43f, .72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(body, paint);

        reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(2.1f));
        paint.setShader(activeGradient(x0, tip, y, .29f));
        canvas.drawPath(lower, paint);

        reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(1.35f));
        paint.setShader(activeGradient(x0, tip, y, .77f));
        canvas.drawPath(upper, paint);

        reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(.55f));
        paint.setColor(0x9affffff);
        canvas.drawPath(upper, paint);

        reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(.78f));
        paint.setShader(activeGradient(x0, tip, y, .90f));
        canvas.drawPath(lower, paint);
    }

    private void drawThumb(Canvas canvas) {
        float x0 = dp(17f), x1 = getWidth() - dp(17f), y = getHeight() * .5f;
        float x = x0 + (x1 - x0) * fraction;
        int color = colorAt(fraction);
        reset();
        paint.setColor(alpha(color, 125));
        paint.setMaskFilter(new BlurMaskFilter(dp(3f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawCircle(x, y, dp(6.8f), paint);
        reset();
        paint.setColor(color);
        canvas.drawCircle(x, y, dp(5.8f), paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dirty || texture == null) rebuild();
        if (texture != null) {
            reset();
            canvas.drawBitmap(texture, 0f, 0f, paint);
        }
        // Reduced motion freezes the ridge, but does not erase its material.
        drawRaisedTrail(canvas);
        drawThumb(canvas);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
            case MotionEvent.ACTION_MOVE:
                float start = dp(17f);
                float span = Math.max(1f, getWidth() - dp(34f));
                setFraction((event.getX() - start) / span);
                if (onSeek != null) onSeek.seek(fraction);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}