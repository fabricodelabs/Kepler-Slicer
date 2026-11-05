package com.keplerslicer.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Shows an approximate 2D layer preview of an app-private slicing result. */
public final class GcodePreviewActivity extends Activity {
    public static final String EXTRA_RESULT_PATH = "result_path";
    private LinearLayout content;
    private MdUi ui;
    private Thread reader;
    private int savedLayer;
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate(Bundle saved) {
        MdUi.prepare(this);
        super.onCreate(saved);
        ui = new MdUi(this);
        savedLayer = saved == null ? 0 : saved.getInt("layer", 0);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);ui.bars(this,root);
        if (Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
        setContentView(root); ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));ui.navigation(this,root,0);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24), dp(24), dp(24), dp(32)); scroll.addView(content);
        content.addView(new com.keplerslicer.app.glass.KeplerUi(this).subHeader(getString(R.string.l10n_ws_layer_preview), getString(R.string.kv3_back), this::finish, null)); gap(12);
        // Title is in the Kepler v3 top bar above.
        TextView state = text(getString(R.string.l10n_gp_reading), 14, false); content.addView(state);
        reader = new Thread(() -> {
            try {
                String path = getIntent().getStringExtra(EXTRA_RESULT_PATH);
                if (path == null) throw new IOException(getString(R.string.l10n_no_slice_result));
                File rootDir = new File(getFilesDir(), "slicer-engine").getCanonicalFile();
                File file = new File(path).getCanonicalFile();
                if (!file.getPath().startsWith(rootDir.getPath() + File.separator) || !file.getName().equals("result.gcode") || !file.isFile()) throw new IOException(getString(R.string.l10n_slice_result_unavailable));
                GcodeAnalysis result = GcodeAnalysis.read(file);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showResult(state, result); });
            } catch (Exception error) {
                String message = EngineText.clean(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) state.setText(getString(R.string.l10n_gp_open_failed) + message); });
            }
        }, "gcode-preview"); reader.start();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("layer", savedLayer); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if (reader != null) reader.interrupt(); super.onDestroy(); }
    private TextView text(String value, int size, boolean bold) { return ui.text(value,size,bold); }
    private void gap(int size) { content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size))); }
    private void showResult(TextView state, GcodeAnalysis result) {
        int count = result.layers.size();
        int last = count % 10, lastTwo = count % 100;
        String suffix = last == 1 && lastTwo != 11 ? L.t("слой") : last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? L.t("слоя") : L.t("слоёв");
        state.setText(count + " " + suffix + getString(R.string.l10n_gp_top_view));
        if (result.layers.isEmpty() || result.segments + result.arcSegments == 0) {
            gap(18); content.addView(text(getString(R.string.l10n_gp_no_paths), 14, true));
        } else {
            gap(16);
            GcodePreviewView preview = new GcodePreviewView(this, result); content.addView(preview, new LinearLayout.LayoutParams(-1, dp(340))); gap(12);
            TextView layerLabel = text("", 15, true); content.addView(layerLabel); gap(8);
            SeekBar slider = new SeekBar(this); slider.setMax(result.layers.size() - 1); content.addView(slider, new LinearLayout.LayoutParams(-1, -2));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int layer, boolean user) {
                    savedLayer = layer; preview.setLayer(layer);
                    double z = result.layers.get(layer).z;
                    layerLabel.setText(getString(R.string.l10n_gp_layer) + (layer + 1) + " / " + result.layers.size() + (Double.isFinite(z) ? String.format(Locale.getDefault(), L.t("  ·  Z %.3f мм"), z) : ""));
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            slider.setProgress(Math.max(0, Math.min(savedLayer, result.layers.size() - 1)));
            // Setting a zero progress may not emit a callback.
            int index = slider.getProgress(); preview.setLayer(index); double z = result.layers.get(index).z;
            layerLabel.setText(getString(R.string.l10n_gp_layer) + (index + 1) + " / " + result.layers.size() + (Double.isFinite(z) ? String.format(Locale.getDefault(), L.t("  ·  Z %.3f мм"), z) : ""));
        }

        gap(20);
        LinearLayout details = ui.vertical(); ui.pad(details,16,16,16,16); details.setBackground(ui.shape(ui.surface,22));
        details.setVisibility(View.GONE);
        LinearLayout disclosure = ui.row(getString(R.string.l10n_gp_details), "", null, () -> { });
        disclosure.setContentDescription(getString(R.string.l10n_gp_details_collapsed));
        disclosure.setOnClickListener(v -> {
            boolean expanded = details.getVisibility() != View.VISIBLE;
            details.setVisibility(expanded ? View.VISIBLE : View.GONE);
            disclosure.setContentDescription(getString(R.string.l10n_gp_details_comma) + (expanded ? getString(R.string.l10n_expanded) : getString(R.string.l10n_collapsed)));
            disclosure.getChildAt(disclosure.getChildCount() - 1).setRotation(expanded ? 90 : 0);
        });
        content.addView(disclosure);
        ui.gap(content,8); content.addView(details);
        details.addView(text(getString(R.string.l10n_gp_linear) + result.segments + getString(R.string.l10n_gp_arcs_shown) + result.renderedArcs, 13, false));
        if (result.segments > 0) {
            ui.gap(details,12);
            details.addView(text(String.format(Locale.getDefault(), L.t("Границы показанных линейных линий во всех слоях: X %.2f…%.2f, Y %.2f…%.2f мм. Ширина %.2f мм, высота %.2f мм. Это не габариты всех траекторий или печатной области."), result.linearMinX, result.linearMaxX, result.linearMinY, result.linearMaxY, result.linearMaxX - result.linearMinX, result.linearMaxY - result.linearMinY), 13, false));
        }
        ui.gap(details,12);
        details.addView(text(L.t("Приблизительный вид сверху: показаны линейные перемещения и поддерживаемые дуги XY с положительной экструзией после маркеров слоёв движка. Дуги заменены отрезками; холостые перемещения, толщина линий, скорость, температура, экструзия без XY и физика печати не отображаются. Макросы и поведение прошивки могут изменить реальные координаты; просмотр не проверяет пригодность к печати."), 13, false));
        if (result.arcMoves + result.unsupportedMoves + result.skippedUnknown + result.malformedLines + result.unknownResets > 0) {
            ui.gap(details,10); details.addView(text(getString(R.string.l10n_gp_arcs_skipped) + result.arcMoves + getString(R.string.l10n_gp_unsupported_moves) + result.unsupportedMoves + getString(R.string.l10n_gp_unknown_segments) + result.skippedUnknown + getString(R.string.l10n_gp_bad_lines) + result.malformedLines + getString(R.string.l10n_gp_xy_resets) + result.unknownResets + ".", 12, false));
        }
        if (result.skippedBeforeLayers > 0) { ui.gap(details,6); details.addView(text(getString(R.string.l10n_gp_preamble) + result.skippedBeforeLayers + ".", 12, false)); }
        if (result.arcTravel > 0) { ui.gap(details,6); details.addView(text(getString(R.string.l10n_gp_arcs_hidden) + result.arcTravel + ".", 12, false)); }
        if (!result.ignoredCommands.isEmpty() || result.ignoredOther > 0) {
            StringBuilder commands = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : result.ignoredCommands.entrySet()) {
                if (commands.length() > 0) commands.append(", ");
                commands.append(entry.getKey()).append(" × ").append(entry.getValue());
            }
            if (result.ignoredOther > 0) { if (commands.length() > 0) commands.append(", "); commands.append(getString(R.string.l10n_gp_others)).append(result.ignoredOther); }
            ui.gap(details,8); details.addView(text(getString(R.string.l10n_gp_cmds_hidden) + commands + getString(R.string.l10n_gp_other_cmds), 12, false));
        }
    }
}
