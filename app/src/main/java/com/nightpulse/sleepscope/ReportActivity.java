package com.nightpulse.sleepscope;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class ReportActivity extends Activity {
    private JSONObject night;
    private String id;
    private SleepRepository repo;
    private MediaPlayer player;
    private TextView playingButton;
    private File exportFile;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        id = getIntent().getStringExtra(SleepMonitorService.EXTRA_ID);
        repo = new SleepRepository(this);
        try { night = repo.get(id); } catch (Exception ignored) { }
        if (night == null) {
            new AlertDialog.Builder(this).setMessage("报告已不存在或文件损坏。")
                    .setPositiveButton("返回", (d, w) -> finish()).show();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Ui.col(this);
        Ui.page(this, root);
        TextView back = Ui.text(this, "‹   返回监测", 14, Ui.MINT, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(Ui.text(this, "睡眠声学报告", 27, Ui.WHITE, true), Ui.lp(this, 23));
        long start = night.optLong("start"), end = night.optLong("end", System.currentTimeMillis());
        root.addView(Ui.text(this, Ui.date(start) + "   ·   " + Ui.clock(start).substring(0, 5) + "—" + Ui.clock(end).substring(0, 5), 13, Ui.MUTED, false), Ui.lp(this, 8));
        if (!night.optBoolean("complete")) {
            root.addView(Ui.text(this, "⚠ 此次监测未正常结束，数据可能不完整", 12, Ui.AMBER, true), Ui.lp(this, 14));
        }
        if (!night.optString("error").isEmpty()) {
            root.addView(Ui.text(this, "录音提示：" + night.optString("error"), 12, Ui.AMBER, false), Ui.lp(this, 8));
        }
        Ui.space(root, this, 20);

        LinearLayout overview = Ui.card(this);
        overview.addView(Ui.text(this, "夜间监测总时长", 13, Ui.MUTED, false));
        overview.addView(Ui.text(this, Ui.duration(night.optLong("durationMs")), 36, Ui.WHITE, true), Ui.lp(this, 7));
        overview.addView(Ui.text(this, "此数值是录音监测时长，不代表真实睡眠时长。", 11, Ui.MUTED, false), Ui.lp(this, 9));
        root.addView(overview);

        JSONArray events = night.optJSONArray("events");
        JSONArray gaps = night.optJSONArray("gaps");
        if (events == null) events = new JSONArray();
        if (gaps == null) gaps = new JSONArray();
        int snoreEvents = 0, voiceEvents = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e != null && "snore".equals(e.optString("type"))) snoreEvents++;
            if (e != null && "voice".equals(e.optString("type"))) voiceEvents++;
        }
        long biggestGap = 0;
        for (int i = 0; i < gaps.length(); i++) biggestGap = Math.max(biggestGap, gaps.optJSONObject(i).optLong("durationMs"));
        LinearLayout features = Ui.row(this);
        features.addView(metric("疑似鼾声", snoreEvents + " 段", Ui.MINT), new LinearLayout.LayoutParams(0, -2, 1));
        Ui.spaceHorizontal(features, this, 9);
        features.addView(metric("疑似人声", voiceEvents + " 段", Ui.BLUE), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(features, Ui.lp(this, 12));
        TextView snoreTime = Ui.text(this, "疑似鼾声检测累计 " + Ui.duration(night.optLong("snoreMs"))
                + "  ·  疑似人声检测累计 " + Ui.duration(night.optLong("voiceMs")), 12, Ui.MUTED, false);
        root.addView(snoreTime, Ui.lp(this, 12));
        LinearLayout gapsCard = Ui.card(this);
        gapsCard.addView(Ui.text(this, "声音间隔观察", 14, Ui.MUTED, true));
        gapsCard.addView(Ui.text(this, gaps.length() + " 次  ·  最长 " + Ui.duration(biggestGap), 19, Ui.AMBER, true), Ui.lp(this, 6));
        gapsCard.addView(Ui.text(this, "仅统计两次疑似鼾声间的较长安静区间（10–90秒），不等于呼吸暂停次数或时长。安静呼吸也可能没有可测声音。", 12, Ui.MUTED, false), Ui.lp(this, 8));
        root.addView(gapsCard, Ui.lp(this, 12));
        if (gaps.length() > 0) {
            LinearLayout gapList = Ui.card(this);
            gapList.addView(Ui.text(this, "安静间隔明细（供观察，非确诊事件）", 13, Ui.AMBER, true));
            int show = Math.min(60, gaps.length());
            for (int i = 0; i < show; i++) {
                JSONObject g = gaps.optJSONObject(i);
                if (g == null) continue;
                gapList.addView(Ui.text(this, Ui.clock(g.optLong("start")) + "  · 约 "
                    + String.format(Locale.CHINA, "%.1f秒", g.optLong("durationMs") / 1000.0),
                    12, Ui.MUTED, false), Ui.lp(this, 7));
            }
            if (show < gaps.length()) gapList.addView(Ui.text(this, "仅显示前60条", 11, Ui.MUTED, false), Ui.lp(this, 8));
            root.addView(gapList, Ui.lp(this, 9));
        }

        Ui.space(root, this, 27);
        root.addView(Ui.text(this, "声音事件分布", 20, Ui.WHITE, true));
        LinearLayout chartBox = Ui.card(this);
        chartBox.addView(Ui.text(this, "● 鼾声     ● 人声 / 疑似梦话", 12, Ui.MUTED, false));
        chartBox.addView(new TimelineView(this, start, start + Math.max(60000, night.optLong("durationMs")), events), Ui.lp(this, 8));
        root.addView(chartBox, Ui.lp(this, 13));

        Ui.space(root, this, 27);
        root.addView(Ui.text(this, "录音片段", 20, Ui.WHITE, true));
        root.addView(Ui.text(this, "点击 ▶ 播放，或导出选中的 WAV 录音", 12, Ui.MUTED, false), Ui.lp(this, 7));
        if (events.length() == 0) {
            LinearLayout empty = Ui.card(this);
            empty.addView(Ui.text(this, "本次未留存符合阈值的鼾声或人声片段", 13, Ui.MUTED, false));
            root.addView(empty, Ui.lp(this, 12));
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            boolean snore = "snore".equals(e.optString("type"));
            File audio = eventFile(e.optString("file"));
            if (audio == null) continue;
            LinearLayout item = Ui.card(this);
            LinearLayout r = Ui.row(this);
            String label = (snore ? "疑似鼾声" : "疑似梦话 / 人声") + "  ·  " + Ui.clock(e.optLong("start"));
            TextView title = Ui.text(this, label, 13, Ui.WHITE, true);
            r.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView play = Ui.text(this, "▶ 播放", 13, snore ? Ui.MINT : Ui.BLUE, true);
            r.addView(play);
            item.addView(r);
            LinearLayout details = Ui.row(this);
            details.addView(Ui.text(this, "片段约 " + Ui.duration(e.optLong("durationMs")), 11, Ui.MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
            TextView export = Ui.text(this, "导出录音  ↗", 11, Ui.MUTED, true);
            details.addView(export);
            item.addView(details, Ui.lp(this, 9));
            play.setOnClickListener(v -> togglePlay(audio, play));
            export.setOnClickListener(v -> exportClip(audio));
            root.addView(item, Ui.lp(this, 9));
        }
        Ui.space(root, this, 27);
        LinearLayout note = Ui.card(this);
        note.setBackground(Ui.bg(0xff102f38, 18, this));
        note.addView(Ui.text(this, "重要 · 监测结果如何理解", 16, Ui.MINT, true));
        note.addView(Ui.text(this,
                "这是基于手机麦克风的声音分析，无法确认是否睡着、是否呼吸停止、血氧变化，也不能区分本人和旁人的声音。梦话与鼾声识别均可能误报或漏报。\n\n如果家人目击呼吸暂停、突然猛吸气或频繁憋醒，应及时到耳鼻喉科或睡眠医学科接受规范的睡眠呼吸监测。", 13, Ui.WHITE, false), Ui.lp(this, 12));
        root.addView(note);
        final int finalSnoreEvents = snoreEvents, finalVoiceEvents = voiceEvents, finalGapCount = gaps.length();
        final long finalBiggestGap = biggestGap;
        TextView share = Ui.button(this, "分享文字报告  ↗", true);
        share.setOnClickListener(v -> shareReport(finalSnoreEvents, finalVoiceEvents, finalGapCount, finalBiggestGap));
        root.addView(share, Ui.lp(this, 20));
        TextView delete = Ui.text(this, "删除本地记录与全部录音", 12, Ui.MUTED, false);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 10));
        delete.setOnClickListener(v -> confirmDelete());
        root.addView(delete);
    }

    private LinearLayout metric(String label, String value, int color) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, value, 23, color, true));
        c.addView(Ui.text(this, label, 12, Ui.MUTED, false), Ui.lp(this, 7));
        return c;
    }

    private File eventFile(String relative) {
        if (!relative.matches("clips/clip_[0-9]{4}\\.wav")) return null;
        try {
            File base = repo.folder(id).getCanonicalFile();
            File file = new File(base, relative).getCanonicalFile();
            return file.getPath().startsWith(base.getPath() + File.separator) && file.isFile() ? file : null;
        } catch (Exception ignored) { return null; }
    }

    private void togglePlay(File audio, TextView target) {
        if (playingButton == target && player != null) { endPlayback(); return; }
        endPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(audio.getAbsolutePath());
            player.setOnCompletionListener(mp -> endPlayback());
            player.prepare();
            player.start();
            playingButton = target;
            target.setText("■ 停止");
        } catch (Exception e) { endPlayback(); Toast.makeText(this, "音频无法播放", Toast.LENGTH_SHORT).show(); }
    }

    private void endPlayback() {
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
        if (playingButton != null) { playingButton.setText("▶ 播放"); playingButton = null; }
    }

    private void exportClip(File file) {
        exportFile = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE, file.getName());
        startActivityForResult(intent, 1001);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 1001 || resultCode != RESULT_OK || data == null || exportFile == null) return;
        try (FileInputStream in = new FileInputStream(exportFile);
             OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IllegalStateException("Unable to open destination");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            Toast.makeText(this, "录音导出成功", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) { Toast.makeText(this, "录音导出失败：" + ex.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void shareReport(int snore, int voice, int gapCount, long biggest) {
        String report = "安睡声迹 · 睡眠声学监测报告\n"
                + Ui.dateTime(night.optLong("start")) + "\n"
                + "监测时长：" + Ui.duration(night.optLong("durationMs")) + "\n"
                + "疑似鼾声片段：" + snore + " 段\n"
                + "疑似人声片段：" + voice + " 段\n"
                + "鼾声检测累计：" + Ui.duration(night.optLong("snoreMs")) + "\n"
                + "声音间隔观察：" + gapCount + " 次（最长 " + Ui.duration(biggest) + "）\n\n"
                + "说明：仅基于声学估计，不能检测或诊断真实呼吸暂停、睡眠分期或血氧变化。";
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(share, "分享报告"));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("永久删除该晚的全部数据？")
            .setMessage("将删除报告和所有 WAV 录音，且无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认删除", (d, w) -> {
                endPlayback();
                if (getSharedPreferences("sleep_scope", MODE_PRIVATE).getBoolean("active", false)
                    && id.equals(getSharedPreferences("sleep_scope", MODE_PRIVATE).getString("active_id", ""))) {
                    Toast.makeText(this, "请先结束正在进行的监测", Toast.LENGTH_LONG).show();
                    return;
                }
                if (repo.delete(id)) finish();
                else Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
            }).show();
    }

    @Override protected void onDestroy() { endPlayback(); super.onDestroy(); }
}
