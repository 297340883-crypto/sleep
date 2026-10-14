package com.nightpulse.sleepscope;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;

/** Horizontal hourly event-count histogram. */
public final class TimelineView extends View {
    private final Paint p = new Paint(3);
    private final long started, ended;
    private final JSONArray events;
    public TimelineView(Context c, long started, long ended, JSONArray events) {
        super(c);
        this.started = started;
        this.ended = Math.max(started + 60000, ended);
        this.events = events == null ? new JSONArray() : events;
    }
    @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(Ui.dp(getContext(), 145), MeasureSpec.EXACTLY)); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float W = getWidth(), H = getHeight(), left = Ui.dp(getContext(), 5), right = W - left;
        float top = Ui.dp(getContext(), 13), chartH = Ui.dp(getContext(), 95);
        int bins = Math.max(6, Math.min(20, (int) Math.ceil((ended - started) / 3600000.0) * 2));
        int[] snores = new int[bins], voices = new int[bins];
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            int slot = (int) ((e.optLong("start") - started) * bins / (ended - started));
            slot = Math.max(0, Math.min(bins - 1, slot));
            if ("snore".equals(e.optString("type"))) snores[slot]++;
            else voices[slot]++;
        }
        int max = 1;
        for (int i = 0; i < bins; i++) max = Math.max(max, snores[i] + voices[i]);
        p.setStrokeWidth(Ui.dp(getContext(), 1));
        p.setColor(0xff315163);
        c.drawLine(left, top + chartH, right, top + chartH, p);
        float step = (right - left) / bins;
        for (int i = 0; i < bins; i++) {
            float x = left + step * i + step * .2f;
            float h1 = chartH * snores[i] / max;
            float h2 = chartH * voices[i] / max;
            p.setColor(Ui.MINT);
            c.drawRoundRect(x, top + chartH - h1, x + step * .57f, top + chartH, 3, 3, p);
            p.setColor(Ui.BLUE);
            c.drawRoundRect(x, top + chartH - h1 - h2, x + step * .57f, top + chartH - h1, 3, 3, p);
        }
        p.setTextSize(Ui.dp(getContext(), 11));
        p.setColor(Ui.MUTED);
        c.drawText(Ui.clock(started).substring(0, 5), left, H - Ui.dp(getContext(), 8), p);
        String endText = Ui.clock(ended).substring(0, 5);
        c.drawText(endText, right - p.measureText(endText), H - Ui.dp(getContext(), 8), p);
    }
}
