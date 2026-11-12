package com.stash.feature.nowplaying.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dirty || texture == null) rebuild();
        if (texture != null) {
            reset();
            canvas.drawBitmap(texture, 0f, 0f, paint);
        }
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