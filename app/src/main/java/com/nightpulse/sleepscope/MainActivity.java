package com.nightpulse.sleepscope;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.List;

public final class MainActivity extends Activity {
    private LinearLayout main, pastNights;
    private TextView indicator, elapsed, snoreCount, voiceCount, gapCount, button, subtitle;
    private AudioLevelView meter;
    private boolean isRegistered;
    private long lastElapsed, lastUpdateAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (active() && lastUpdateAt > 0) {
                long estimate = lastElapsed + (android.os.SystemClock.elapsedRealtime() - lastUpdateAt);
                elapsed.setText(Ui.timer(estimate));
            }
            handler.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (SleepMonitorService.UPDATE.equals(intent.getAction())) {
                lastElapsed = intent.getLongExtra("durationMs", 0);
                lastUpdateAt = android.os.SystemClock.elapsedRealtime();
                elapsed.setText(Ui.timer(lastElapsed));
                snoreCount.setText(String.valueOf(intent.getIntExtra("snoreEvents", 0)));
                voiceCount.setText(String.valueOf(intent.getIntExtra("voiceEvents", 0)));
                gapCount.setText(String.valueOf(intent.getIntExtra("gapEvents", 0)));
                meter.setLevel(intent.getDoubleExtra("level", 0));
                setStatus(true);
            } else if (SleepMonitorService.FINISHED.equals(intent.getAction())) {
                setStatus(false);
                populateHistory();
                String id = intent.getStringExtra(SleepMonitorService.EXTRA_ID);
                if (id != null) openReport(id);
            }
        }
    };

    private SharedPreferences prefs() { return getSharedPreferences("sleep_scope", MODE_PRIVATE); }
    private boolean active() { return prefs().getBoolean("active", false); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createUi();
        handler.post(ticker);
    }

    private void createUi() {
        main = Ui.col(this);
        Ui.page(this, main);

        LinearLayout top = Ui.row(this);
        LinearLayout brand = Ui.col(this);
        brand.addView(Ui.text(this, "☾  安睡声迹", 24, Ui.WHITE, true));
        brand.addView(Ui.text(this, "SLEEP SCOPE  /  PRIVATE SLEEP JOURNAL", 9, Ui.MUTED, true), Ui.lp(this, 5));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView gear = Ui.text(this, "⚙", 26, Ui.MINT, true);
        gear.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        gear.setOnClickListener(v -> settings());
        top.addView(gear);
        main.addView(top);
        Ui.space(main, this, 28);

        indicator = Ui.text(this, "●  等待开始监测", 13, Ui.MINT, true);
        main.addView(indicator);
        subtitle = Ui.text(this, "聆听夜晚，读懂睡眠的声音", 16, Ui.MUTED, false);
        main.addView(subtitle, Ui.lp(this, 9));
        Ui.space(main, this, 23);

        LinearLayout monitor = Ui.card(this);
        monitor.setBackground(Ui.border(Ui.CARD, 0xff204b52, 22, this));
        monitor.addView(Ui.text(this, "本次监测时长", 13, Ui.MUTED, true));
        elapsed = Ui.text(this, "00:00:00", 44, Ui.WHITE, true);
        elapsed.setLetterSpacing(.065f);
        monitor.addView(elapsed, Ui.lp(this, 8));
        monitor.addView(Ui.text(this, "实时环境声量", 11, Ui.MUTED, false), Ui.lp(this, 19));
        meter = new AudioLevelView(this);
        monitor.addView(meter, Ui.lp(this, 5));
        main.addView(monitor);

        Ui.space(main, this, 14);
        LinearLayout stats = Ui.row(this);
        stats.addView(metric("疑似鼾声", "0", Ui.MINT, 0), new LinearLayout.LayoutParams(0, -2, 1));
        Ui.spaceHorizontal(stats, this, 9);
        stats.addView(metric("疑似梦话", "0", Ui.BLUE, 1), new LinearLayout.LayoutParams(0, -2, 1));
        Ui.spaceHorizontal(stats, this, 9);
        stats.addView(metric("声音间隔", "0", Ui.AMBER, 2), new LinearLayout.LayoutParams(0, -2, 1));
        main.addView(stats);

        button = Ui.button(this, "开始睡眠监测  →", true);
        button.setOnClickListener(v -> { if (active()) confirmStop(); else startWithPermission(); });
        main.addView(button, Ui.lp(this, 22));

        TextView hint = Ui.text(this, "锁屏后仍可继续监测 · 手机放置床头 · 建议接通电源", 11, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        main.addView(hint, Ui.lp(this, 10));
        Ui.space(main, this, 32);

        main.addView(Ui.text(this, "睡眠记录", 21, Ui.WHITE, true));
        pastNights = Ui.col(this);
        main.addView(pastNights, Ui.lp(this, 12));
        Ui.space(main, this, 22);

        LinearLayout privacy = Ui.card(this);
        privacy.setBackground(Ui.bg(0xff0b2632, 18, this));
        privacy.addView(Ui.text(this, "◈  仅存储在本机", 14, Ui.MINT, true));
        privacy.addView(Ui.text(this, "无账号、无云上传。仅保存检测到的声学事件短录音，而非整晚连续录音。", 12, Ui.MUTED, false), Ui.lp(this, 8));
        privacy.addView(Ui.text(this, "本应用无法通过手机麦克风确认呼吸暂停；“声音间隔”只是粗略声学线索，不能作为诊断或排除睡眠呼吸暂停的依据。", 12, Ui.MUTED, false), Ui.lp(this, 9));
        main.addView(privacy);
        setStatus(active());
        populateHistory();
    }

    private LinearLayout metric(String label, String value, int color, int slot) {
        LinearLayout item = Ui.col(this);
        item.setPadding(Ui.dp(this, 12), Ui.dp(this, 17), Ui.dp(this, 8), Ui.dp(this, 15));
        item.setBackground(Ui.bg(Ui.CARD, 16, this));
        TextView number = Ui.text(this, value, 29, color, true);
        item.addView(number);
        item.addView(Ui.text(this, label, 11, Ui.MUTED, false), Ui.lp(this, 5));
        if (slot == 0) snoreCount = number;
        else if (slot == 1) voiceCount = number;
        else gapCount = number;
        return item;
    }

    private void setStatus(boolean isActive) {
        if (indicator == null) return;
        indicator.setText(isActive ? "●  正在监测 · 麦克风运行中" : "●  等待开始监测");
        subtitle.setText(isActive ? "检测到的疑似鼾声与人声将保存为短录音" : "聆听夜晚，读懂睡眠的声音");
        button.setText(isActive ? "结束睡眠 · 生成报告" : "开始睡眠监测  →");
        button.setBackground(Ui.bg(isActive ? 0xff283f53 : Ui.MINT, 18, this));
        button.setTextColor(isActive ? Ui.WHITE : Ui.BG);
        if (!isActive) {
            lastUpdateAt = 0;
            elapsed.setText("00:00:00");
            snoreCount.setText("0");
            voiceCount.setText("0");
            gapCount.setText("0");
        }
    }

    private void startWithPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 62);
        } else startSession();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] grants) {
        super.onRequestPermissionsResult(code, p, grants);
        if (code == 62) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startSession();
            else new AlertDialog.Builder(this).setMessage("需要麦克风权限才能检测鼾声和保存录音。请在系统设置中授予该权限。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    private void startSession() {
        try {
            startForegroundService(new Intent(this, SleepMonitorService.class).setAction(SleepMonitorService.START));
            setStatus(true);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 63);
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("无法开始监测").setMessage(e.getMessage()).setPositiveButton("好的", null).show();
        }
    }

    private void confirmStop() {
        new AlertDialog.Builder(this).setTitle("结束本次睡眠监测？")
            .setMessage("系统将完成录音保存，并生成本次声学监测报告。")
            .setNegativeButton("继续监测", null)
            .setPositiveButton("结束并生成", (d, w) -> {
                stopServiceByAction();
                button.setText("正在生成报告…");
            }).show();
    }

    private void stopServiceByAction() {
        // Send command via a started service, not stopService(), so its finally block can close WAV headers.
        startService(new Intent(this, SleepMonitorService.class).setAction(SleepMonitorService.STOP));
    }

    private void settings() {
        String[] items = {"低 · 减少误报", "标准 · 推荐", "高 · 易受环境声影响"};
        int current = prefs().getInt("sensitivity", 2) - 1;
        new AlertDialog.Builder(this).setTitle("声学检测灵敏度")
            .setSingleChoiceItems(items, current, (dialog, which) -> {
                prefs().edit().putInt("sensitivity", which + 1).apply();
                dialog.dismiss();
                new AlertDialog.Builder(this).setMessage("已保存。新灵敏度将在下一次开始监测时生效。")
                        .setPositiveButton("确定", null).show();
            })
            .setNeutralButton("后台省电设置", (dialog, which) -> {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) { }
            })
            .setNegativeButton("取消", null).show();
    }

    private void populateHistory() {
        if (pastNights == null) return;
        pastNights.removeAllViews();
        List<JSONObject> nights = new SleepRepository(this).list();
        if (nights.isEmpty()) {
            LinearLayout placeholder = Ui.card(this);
            placeholder.addView(Ui.text(this, "✧  还没有睡眠记录", 15, Ui.WHITE, true));
            placeholder.addView(Ui.text(this, "完成一次夜间监测后，睡眠报告会出现在这里。", 12, Ui.MUTED, false), Ui.lp(this, 7));
            pastNights.addView(placeholder);
            return;
        }
        int limit = Math.min(12, nights.size());
        for (int i = 0; i < limit; i++) {
            JSONObject j = nights.get(i);
            String id = j.optString("id");
            LinearLayout item = Ui.card(this);
            LinearLayout first = Ui.row(this);
            TextView date = Ui.text(this, Ui.date(j.optLong("start")), 16, Ui.WHITE, true);
            first.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
            first.addView(Ui.text(this, "查看报告  ›", 12, Ui.MINT, true));
            item.addView(first);
            item.addView(Ui.text(this, "监测 " + Ui.duration(j.optLong("durationMs")) + "    ·    "
                    + j.optJSONArray("events").length() + " 段声学事件" + (j.optBoolean("complete") ? "" : "  ·  未完成"),
                12, Ui.MUTED, false), Ui.lp(this, 8));
            item.setOnClickListener(v -> openReport(id));
            pastNights.addView(item, Ui.lp(this, i == 0 ? 0 : 9));
        }
    }

    private void openReport(String id) {
        Intent intent = new Intent(this, ReportActivity.class).putExtra(SleepMonitorService.EXTRA_ID, id);
        startActivity(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        // A system-killed process cannot silently resume mic capture from background.
        if (active() && !SleepMonitorService.IS_MONITORING) {
            prefs().edit().putBoolean("active", false).remove("active_id").apply();
        }
        if (!isRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SleepMonitorService.UPDATE);
            filter.addAction(SleepMonitorService.FINISHED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
            isRegistered = true;
        }
        setStatus(active());
        populateHistory();
    }
    @Override protected void onPause() {
        if (isRegistered) { unregisterReceiver(receiver); isRegistered = false; }
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }
}
