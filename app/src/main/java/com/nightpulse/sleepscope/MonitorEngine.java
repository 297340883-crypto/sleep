package com.nightpulse.sleepscope;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Locale;

/** Session event aggregation and short WAV clips; only snore/voice candidates are retained. */
public final class MonitorEngine {
    private final SleepRepository repo;
    private final String id;
    private final JSONObject json;
    private final SoundClassifier classifier;
    private final long wallStarted, elapsedStarted;
    private final ArrayDeque<short[]> previous = new ArrayDeque<>();
    private static final int BLOCK = 2048;
    private static final long BLOCK_MS = BLOCK * 1000L / 16000L;
    private static final long MAX_CLIP_BYTES = 35 * 16000L * 2;
    private static final long MAX_SESSION_AUDIO_BYTES = 120L * 1024 * 1024;
    private int consecutiveSnore, consecutiveVoice;
    private long idleFrames, gapQuietMs, snoreEndedElapsed = -1;
    private long snoreMs, voiceMs;
    private int clipCount;
    private long savedAudioBytes;
    private boolean gapInvalid;
    private WavClip currentClip;
    private int currentKind;
    private long eventStarted;
    private long lastSoundElapsed;
    private long lastSavedElapsed;
    private double lastRms;
    private String failure = "";

    public MonitorEngine(Context context, String id, int sensitivity) throws Exception {
        this.repo = new SleepRepository(context);
        this.id = id;
        this.wallStarted = System.currentTimeMillis();
        this.elapsedStarted = SystemClock.elapsedRealtime();
        this.json = repo.create(id, wallStarted);
        this.classifier = new SoundClassifier(sensitivity);
    }

    public void process(short[] samples, int len) {
        long nowElapsed = SystemClock.elapsedRealtime();
        SoundClassifier.Result result = classifier.analyze(samples, len);
        lastRms = result.relativeLevel;
        int kind = result.kind;
        if (kind == SoundClassifier.SNORE) snoreMs += BLOCK_MS;
        if (kind == SoundClassifier.VOICE) voiceMs += BLOCK_MS;
        if (kind == SoundClassifier.SNORE) consecutiveSnore++; else consecutiveSnore = 0;
        if (kind == SoundClassifier.VOICE) consecutiveVoice++; else consecutiveVoice = 0;

        if (snoreEndedElapsed > 0 && currentClip == null) {
            if (kind == SoundClassifier.QUIET) gapQuietMs += BLOCK_MS;
            if (kind == SoundClassifier.VOICE || kind == SoundClassifier.OTHER) gapInvalid = true;
        }

        if (currentClip == null && clipCount < 350 && savedAudioBytes < MAX_SESSION_AUDIO_BYTES) {
            int detected = 0;
            if (consecutiveSnore >= 4) detected = SoundClassifier.SNORE;
            else if (consecutiveVoice >= 4) detected = SoundClassifier.VOICE;
            if (detected != 0) begin(detected, nowElapsed);
        }

        if (currentClip != null) {
            try {
                currentClip.write(samples, len);
                if (kind == currentKind) lastSoundElapsed = nowElapsed;
                if (nowElapsed - lastSoundElapsed > 1700 || currentClip.bytes() >= MAX_CLIP_BYTES) {
                    closeCurrent(nowElapsed);
                }
            } catch (Exception e) { failure = "录音片段写入失败：" + e.getClass().getSimpleName(); closeCurrent(nowElapsed); }
        }
        previous.addLast(copy(samples, len));
        while (previous.size() > 15) previous.removeFirst(); // ~1.9 seconds of preroll
        if (nowElapsed - lastSavedElapsed > 20000) checkpoint(false);
    }

    private static short[] copy(short[] samples, int len) {
        short[] dest = new short[len];
        System.arraycopy(samples, 0, dest, 0, len);
        return dest;
    }

    private void begin(int kind, long nowElapsed) {
        if (kind == SoundClassifier.SNORE && snoreEndedElapsed > 0) {
            long gapMs = nowElapsed - snoreEndedElapsed;
            if (gapMs >= 10000 && gapMs <= 90000 && !gapInvalid && gapQuietMs >= gapMs * 0.70) {
                try {
                    JSONObject gap = new JSONObject();
                    gap.put("start", wallStarted + (snoreEndedElapsed - elapsedStarted));
                    gap.put("durationMs", gapMs);
                    json.getJSONArray("gaps").put(gap);
                } catch (Exception ignored) { }
            }
        }
        if (kind == SoundClassifier.SNORE) {
            snoreEndedElapsed = -1;
            gapQuietMs = 0;
            gapInvalid = false;
        }
        try {
            File dir = new File(repo.folder(id), "clips");
            String name = String.format(Locale.US, "clip_%04d.wav", ++clipCount);
            currentClip = new WavClip(new File(dir, name));
            currentKind = kind;
            eventStarted = Math.max(wallStarted, System.currentTimeMillis() - previous.size() * BLOCK_MS);
            lastSoundElapsed = nowElapsed;
            for (short[] samples : previous) currentClip.write(samples, samples.length);
        } catch (Exception e) {
            currentClip = null;
            failure = "无法建立录音文件：" + e.getMessage();
        }
    }

    private void closeCurrent(long nowElapsed) {
        if (currentClip == null) return;
        WavClip clip = currentClip;
        currentClip = null;
        try {
            clip.close();
            // Tiny events are more likely to be knocks/background noise; do not clutter the report.
            if (clip.bytes() < 8000) {
                clip.file().delete();
            } else {
                JSONObject e = new JSONObject();
                e.put("type", currentKind == SoundClassifier.SNORE ? "snore" : "voice");
                e.put("start", eventStarted);
                e.put("durationMs", Math.max(1, clip.bytes() / 32));
                e.put("file", "clips/" + clip.file().getName());
                json.getJSONArray("events").put(e);
                savedAudioBytes += clip.bytes() + 44;
                if (savedAudioBytes >= MAX_SESSION_AUDIO_BYTES) {
                    failure = "录音总量达到120MB上限，后续仅继续监测，不再保存声音片段";
                }
            }
        } catch (Exception e) { failure = "写入录音信息失败：" + e.getMessage(); }
        if (currentKind == SoundClassifier.SNORE) {
            snoreEndedElapsed = nowElapsed;
            gapQuietMs = 0;
            gapInvalid = false;
        }
        checkpoint(false);
    }

    public void setFailure(String message) { failure = message; }

    public void checkpoint(boolean complete) {
        long now = SystemClock.elapsedRealtime();
        try {
            json.put("durationMs", Math.max(0, now - elapsedStarted));
            json.put("snoreMs", snoreMs);
            json.put("voiceMs", voiceMs);
            json.put("error", failure);
            json.put("complete", complete);
            if (complete) json.put("end", System.currentTimeMillis());
            repo.save(id, json);
            lastSavedElapsed = now;
        } catch (Exception e) { android.util.Log.e("MonitorEngine", "checkpoint", e); }
    }

    public void finish() {
        closeCurrent(SystemClock.elapsedRealtime());
        checkpoint(true);
    }

    public double currentLevel() { return lastRms; }
    public long elapsedMs() { return SystemClock.elapsedRealtime() - elapsedStarted; }
    public long snoreMs() { return snoreMs; }
    public long voiceMs() { return voiceMs; }
    public int snoreEvents() { return countType("snore"); }
    public int voiceEvents() { return countType("voice"); }
    public int gapEvents() { return json.optJSONArray("gaps") == null ? 0 : json.optJSONArray("gaps").length(); }
    private int countType(String type) {
        JSONArray a = json.optJSONArray("events");
        if (a == null) return 0;
        int count = 0;
        for (int i = 0; i < a.length(); i++) if (type.equals(a.optJSONObject(i).optString("type"))) count++;
        return count;
    }
}
