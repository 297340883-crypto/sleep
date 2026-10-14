package com.nightpulse.sleepscope;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Every session and its audio clips remain in the app's private local storage. */
public final class SleepRepository {
    private final File root;

    public SleepRepository(Context c) {
        root = new File(c.getFilesDir(), "sleep_nights");
        if (!root.exists()) root.mkdirs();
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[0-9]{13,18}");
    }

    public File folder(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid night ID");
        return new File(root, id);
    }

    public JSONObject create(String id, long started) throws JSONException {
        File dir = folder(id);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create session folder");
        JSONObject j = new JSONObject();
        j.put("schema", 1);
        j.put("id", id);
        j.put("start", started);
        j.put("end", 0);
        j.put("durationMs", 0);
        j.put("snoreMs", 0);
        j.put("voiceMs", 0);
        j.put("events", new JSONArray());
        j.put("gaps", new JSONArray());
        j.put("complete", false);
        j.put("error", "");
        save(id, j);
        return j;
    }

    public synchronized void save(String id, JSONObject data) {
        try {
            File dir = folder(id);
            if (!dir.exists()) dir.mkdirs();
            File target = new File(dir, "record.json");
            File temp = new File(dir, "record.json.new");
            byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                out.getFD().sync();
            }
            if (!temp.renameTo(target)) {
                try (FileOutputStream out = new FileOutputStream(target)) { out.write(bytes); out.getFD().sync(); }
                temp.delete();
            }
        } catch (Exception ex) { android.util.Log.e("SleepRepository", "Cannot save night", ex); }
    }

    public JSONObject get(String id) {
        try {
            File f = new File(folder(id), "record.json");
            if (!f.exists()) return null;
            try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return new JSONObject(b.toString("UTF-8"));
            }
        } catch (Exception ignored) { return null; }
    }

    public boolean delete(String id) {
        try { return deleteRecursively(folder(id)); } catch (Exception ignored) { return false; }
    }

    private static boolean deleteRecursively(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File child : kids) if (!deleteRecursively(child)) return false;
        }
        return f.delete();
    }

    public List<JSONObject> list() {
        List<JSONObject> out = new ArrayList<>();
        File[] dirs = root.listFiles();
        if (dirs == null) return out;
        Arrays.sort(dirs, (a,b) -> b.getName().compareTo(a.getName()));
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            JSONObject j = get(dir.getName());
            if (j != null) out.add(j);
        }
        return out;
    }
}
