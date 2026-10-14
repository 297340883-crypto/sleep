package com.nightpulse.sleepscope;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Displays rolling microphone volume; not a respiration trace. */
public final class AudioLevelView extends View {
    private static final int COUNT = 45;
    private final double[] levels = new double[COUNT];
    private final Paint paint = new Paint(3);
    public AudioLevelView(Context c) { super(c); setMinimumHeight(Ui.dp(c, 76)); }
    public void setLevel(double level) {
        System.arraycopy(levels, 1, levels, 0, levels.length - 1);
        levels[levels.length - 1] = Math.max(0, Math.min(1, level));
        invalidate();
    }
    @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(Ui.dp(getContext(), 80), MeasureSpec.EXACTLY)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), step = w / COUNT;
        for (int i = 0; i < COUNT; i++) {
            float level = (float) levels[i];
            float bar = Math.max(Ui.dp(getContext(), 3), (h * .85f) * level);
            paint.setColor(i > COUNT - 8 ? Ui.MINT : 0xff416b75);
            canvas.drawRoundRect(i * step + 1, (h - bar) / 2, i * step + step * .62f, (h + bar) / 2, step * .25f, step * .25f, paint);
        }
    }
}
