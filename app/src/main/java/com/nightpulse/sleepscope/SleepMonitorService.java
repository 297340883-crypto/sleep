package com.nightpulse.sleepscope;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/** Visible, user-started microphone foreground service that keeps running when the screen locks. */
public final class SleepMonitorService extends Service {
    public static volatile boolean IS_MONITORING = false;
    public static final String START = "com.nightpulse.sleepscope.START";
    public static final String STOP = "com.nightpulse.sleepscope.STOP";
    public static final String UPDATE = "com.nightpulse.sleepscope.UPDATE";
    public static final String FINISHED = "com.nightpulse.sleepscope.FINISHED";
    public static final String EXTRA_ID = "night_id";
    private static final String CHANNEL = "sleep_recording";
    private static final int NOTIFICATION_ID = 18;
    private final Object lock = new Object();
    private volatile boolean running;
    private volatile MonitorEngine engine;
    private volatile boolean started;
    private PowerManager.WakeLock wakeLock;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (STOP.equals(action)) {
            running = false;
            if (!started) stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(action) || started) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        started = true;
        running = true;
        IS_MONITORING = true;
        showForeground();
        new Thread(this::recordNight, "NightAudioCapture").start();
        return START_NOT_STICKY; // No impermissible auto-relaunch of microphone from background.
    }

    private void showForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "睡眠监测运行中", NotificationManager.IMPORTANCE_LOW));
        Intent tap = new Intent(this, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent home = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, SleepMonitorService.class).setAction(STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(com.nightpulse.sleepscope.R.drawable.ic_moon)
                .setContentTitle("安睡声迹 · 正在监测")
                .setContentText("鼾声 / 疑似人声分析中 · 点击返回应用")
                .setContentIntent(home)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "结束并保存", stopPending).build())
                .setOngoing(true).setOnlyAlertOnce(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else startForeground(NOTIFICATION_ID, n);
    }

    private void recordNight() {
        AudioRecord record = null;
        String id = String.valueOf(System.currentTimeMillis());
        SharedPreferences prefs = getSharedPreferences("sleep_scope", Context.MODE_PRIVATE);
        try {
            int sensitivity = prefs.getInt("sensitivity", 2);
            engine = new MonitorEngine(this, id, sensitivity);
            prefs.edit().putBoolean("active", true).putString("active_id", id).apply();
            broadcast(UPDATE, id);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepScope:NightMonitoring");
            wakeLock.acquire(); // Released deterministically in finally; can increase overnight battery usage.
            final int rate = 16000;
            final int channel = AudioFormat.CHANNEL_IN_MONO;
            final int format = AudioFormat.ENCODING_PCM_16BIT;
            int min = AudioRecord.getMinBufferSize(rate, channel, format);
            if (min < 0) throw new IllegalStateException("当前设备不支持16kHz录音");
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channel, format, Math.max(16384, min * 2));
            if (record.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("麦克风未能初始化");
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("麦克风录音未启动");
            short[] block = new short[2048];
            long lastUpdate = SystemClock.elapsedRealtime();
            int failures = 0;
            while (running) {
                int n = record.read(block, 0, block.length, AudioRecord.READ_BLOCKING);
                if (n == block.length) {
                    engine.process(block, n);
                    failures = 0;
                } else if (n < 0) {
                    failures++;
                    if (failures > 10) throw new IllegalStateException("录音中断（错误码 " + n + "）");
                    SystemClock.sleep(100);
                }
                if (SystemClock.elapsedRealtime() - lastUpdate >= 1800) {
                    broadcast(UPDATE, id);
                    lastUpdate = SystemClock.elapsedRealtime();
                }
            }
        } catch (Exception ex) {
            Log.e("SleepMonitor", "Audio capture ended", ex);
            if (engine != null) engine.setFailure(ex.getMessage() == null ? "监测意外停止" : ex.getMessage());
        } finally {
            running = false;
            IS_MONITORING = false;
            if (record != null) {
                try { if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop(); } catch (Exception ignored) { }
                record.release();
            }
            if (engine != null) engine.finish();
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            prefs.edit().putBoolean("active", false).remove("active_id").apply();
            broadcast(FINISHED, id);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void broadcast(String action, String id) {
        Intent update = new Intent(action).setPackage(getPackageName()).putExtra(EXTRA_ID, id);
        if (engine != null) {
            update.putExtra("durationMs", engine.elapsedMs());
            update.putExtra("snoreMs", engine.snoreMs());
            update.putExtra("snoreEvents", engine.snoreEvents());
            update.putExtra("voiceEvents", engine.voiceEvents());
            update.putExtra("gapEvents", engine.gapEvents());
            update.putExtra("level", engine.currentLevel());
        }
        sendBroadcast(update);
    }

    @Override public void onDestroy() {
        running = false;
        super.onDestroy();
    }
}
