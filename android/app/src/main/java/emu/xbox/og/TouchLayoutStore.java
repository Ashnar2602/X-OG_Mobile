package emu.xbox.og;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists per-control {@link TouchControlOverride}s as JSON in SharedPreferences. */
final class TouchLayoutStore {
    private static final String PREFS = "touch_layout";
    private static final String KEY = "layout_v1";

    private TouchLayoutStore() {
    }

    static Map<String, TouchControlOverride> load(Context context) {
        Map<String, TouchControlOverride> result = new LinkedHashMap<>();
        String json = prefs(context).getString(KEY, null);
        if (json == null) {
            return result;
        }
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject o = root.getJSONObject(key);
                TouchControlOverride ov = new TouchControlOverride();
                ov.dx = (float) o.optDouble("dx", 0);
                ov.dy = (float) o.optDouble("dy", 0);
                ov.scale = (float) o.optDouble("scale", 1);
                ov.scale2 = (float) o.optDouble("scale2", 1);
                ov.alpha = (float) o.optDouble("alpha", 1);
                result.put(key, ov);
            }
        } catch (JSONException e) {
            return new LinkedHashMap<>();
        }
        return result;
    }

    static void save(Context context, Map<String, TouchControlOverride> overrides) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, TouchControlOverride> entry : overrides.entrySet()) {
                TouchControlOverride ov = entry.getValue();
                JSONObject o = new JSONObject();
                o.put("dx", ov.dx);
                o.put("dy", ov.dy);
                o.put("scale", ov.scale);
                o.put("scale2", ov.scale2);
                o.put("alpha", ov.alpha);
                root.put(entry.getKey(), o);
            }
        } catch (JSONException e) {
            return;
        }
        prefs(context).edit().putString(KEY, root.toString()).apply();
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
