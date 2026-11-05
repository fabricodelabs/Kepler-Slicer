package com.keplerslicer.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.keplerslicer.app.glass.GlassBottomSheet;
import com.keplerslicer.app.glass.KeplerUi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-material printing: assigns material slots (Filament screen, slots 1..4) to objects and parts.
 * The engine writes a derived project 3MF with every slot, its colour and recalculated flushing volumes;
 * the source model stays unchanged.
 */
final class MaterialAssignment {
    private static final class Target { final int object, part; final String label; int slot; Target(int o, int p, String l, int s) { object = o; part = p; label = l; slot = s; } }
    private MaterialAssignment() {}

    static void open(Activity activity, Runnable changed) {
        Toast.makeText(activity, R.string.pref_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject layout = NativePreferenceActions.materialLayout(activity.getApplicationContext());
                activity.runOnUiThread(() -> { if (!activity.isDestroyed()) show(activity, layout, changed); });
            } catch (Exception e) { failed(activity, e); }
        }, "material-layout").start();
    }

    private static void show(Activity activity, JSONObject layout, Runnable changed) {
        final MaterialSlots.Slot[] slots = MaterialSlots.load(activity);
        final List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) if (slots[i].filled()) filled.add(i);
        final String title = activity.getString(R.string.kv3_materials_title);
        if (filled.size() < 2) {
            MdUi.alert(activity).setTitle(title).setMessage(R.string.kv3_materials_need_slots)
                .setPositiveButton(R.string.kv3_materials_open_slots, (d, w) -> activity.startActivity(new Intent(activity, ProfilePickerActivity.class).putExtra(ProfilePickerActivity.EXTRA_KIND, PrintProfileCatalog.FILAMENT)))
                .setNegativeButton(R.string.general_project_cancel, null).show();
            return;
        }
        final List<Target> targets = new ArrayList<>();
        JSONArray objects = layout.optJSONArray("objects");
        for (int o = 0; objects != null && o < objects.length(); o++) {
            JSONObject object = objects.optJSONObject(o); if (object == null) continue;
            String name = object.optString("name", "").trim(); if (name.isEmpty()) name = activity.getString(R.string.kv3_materials_object, o + 1);
            int objectSlot = Math.max(1, object.optInt("slot", 0));
            JSONArray parts = object.optJSONArray("parts");
            if (parts != null && parts.length() > 1) {
                for (int p = 0; p < parts.length(); p++) {
                    JSONObject part = parts.optJSONObject(p); String pn = part == null ? "" : part.optString("name", "").trim();
                    if (pn.isEmpty()) pn = activity.getString(R.string.kv3_materials_part, p + 1);
                    int s = part == null ? 0 : part.optInt("slot", 0);
                    targets.add(new Target(o, p, name + " · " + pn, uiSlot(s > 0 ? s : objectSlot, filled)));
                }
            } else targets.add(new Target(o, -1, name, uiSlot(objectSlot, filled)));
        }
        if (targets.isEmpty()) { GlassBottomSheet.info(activity, activity.getWindow().getDecorView(), false, title, activity.getString(R.string.kv3_no_model_detail)); return; }

        KeplerUi ui = new KeplerUi(activity);
        LinearLayout list = ui.column(); list.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), 0);
        TextView hint = ui.line(activity.getString(R.string.kv3_materials_hint), 13, 400, KeplerUi.SECONDARY); hint.setSingleLine(false); hint.setMaxLines(6); hint.setEllipsize(null); list.addView(hint);
        final String[] labels = new String[filled.size()];
        for (int i = 0; i < filled.size(); i++) labels[i] = slotLabel(activity, filled.get(i), slots[filled.get(i)]);
        for (Target t : targets) {
            LinearLayout row = ui.row(); row.setMinimumHeight(ui.dp(52)); row.setPadding(0, ui.dp(4), 0, ui.dp(4));
            View dot = new View(activity); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setStroke(Math.max(1, ui.dp(1)), 0x66FFFFFF); dot.setBackground(g); row.addView(dot, ui.lp(22, 22));
            TextView name = ui.line(t.label, 14, 600, KeplerUi.WHITE); LinearLayout.LayoutParams np = ui.weight(); np.leftMargin = ui.dp(12); np.rightMargin = ui.dp(8); row.addView(name, np);
            TextView slot = ui.line("", 13, 600, KeplerUi.MINT); slot.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6)); slot.setBackground(ui.shape(0x2E5EE0C8, 0, 10)); row.addView(slot);
            Runnable paint = () -> { int index = filled.get(t.slot); g.setColor(MaterialSlots.argb(slots[index].colour)); slot.setText(activity.getString(R.string.kv3_slot, index + 1)); row.setContentDescription(t.label + ", " + labels[t.slot]); };
            paint.run();
            ui.pressable(row, null, 12);
            row.setOnClickListener(v -> GlassBottomSheet.choose(activity, activity.getWindow().getDecorView(), false, t.label, labels, t.slot, which -> { t.slot = which; paint.run(); }));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView scroll = new ScrollView(activity); scroll.addView(list);
        android.app.AlertDialog.Builder builder = MdUi.alert(activity).setTitle(title).setView(scroll).setNegativeButton(R.string.general_project_cancel, null);
        if (activity instanceof ModelDetailsActivity) builder.setNeutralButton(R.string.kv3_paint_colour_brush, (d, w) -> { activity.setResult(Activity.RESULT_OK, new Intent().putExtra("paint_colour", true)); activity.finish(); });
        builder
            .setPositiveButton(R.string.kv3_materials_apply, (d, w) -> apply(activity, slots, filled, targets, changed)).show();
    }

    private static int uiSlot(int engineSlot, List<Integer> filled) {
        // Engine slots are numbered over the filled slots in order (see MaterialSlots.request).
        int n = engineSlot - 1; return n >= 0 && n < filled.size() ? n : 0;
    }
    private static String slotLabel(Activity a, int index, MaterialSlots.Slot s) {
        int at = s.name.indexOf(" @"); String name = at > 0 ? s.name.substring(0, at) : s.name;
        return a.getString(R.string.kv3_slot, index + 1) + " · " + EngineText.clean(name);
    }

    private static void apply(Activity activity, MaterialSlots.Slot[] slots, List<Integer> filled, List<Target> targets, Runnable changed) {
        try {
            int[] map = new int[slots.length];
            JSONObject request = MaterialSlots.request(slots, map);
            JSONArray assign = new JSONArray();
            for (Target t : targets) assign.put(new JSONObject().put("object", t.object).put("part", t.part).put("slot", map[filled.get(t.slot)]));
            request.put("assign", assign);
            final String json = request.toString();
            Toast.makeText(activity, R.string.pref_working, Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    NativePreferenceActions.editModel(activity.getApplicationContext(), NativePreferenceActions.EDIT_MATERIALS, 0, 0, json);
                    activity.runOnUiThread(() -> { if (!activity.isDestroyed()) { ControlPreferences.scheduleAutoSlice(activity); changed.run(); } });
                } catch (Exception | LinkageError e) { failed(activity, e); }
            }, "material-assign").start();
        } catch (Exception e) { failed(activity, e); }
    }

    private static void failed(Activity activity, Throwable e) {
        final String m = EngineText.clean(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        activity.runOnUiThread(() -> { if (!activity.isDestroyed()) GlassBottomSheet.info(activity, activity.getWindow().getDecorView(), false, activity.getString(R.string.kv3_edit_failed), m); });
    }
}
