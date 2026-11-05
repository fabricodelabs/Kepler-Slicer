package com.keplerslicer.app.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.keplerslicer.app.R;
import com.keplerslicer.app.glass.GlassBottomSheet;
import com.keplerslicer.app.glass.GlassTypography;
import com.keplerslicer.app.glass.GlassUi;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.function.Consumer;

/** Edits a value in memory only. Persistence, validation by the engine and inherited values belong to the caller. */
public final class SettingsEditorDialog {
    private SettingsEditorDialog() {}

    public static void show(Activity activity, View anchor, boolean reduced, SettingsSchema.Option option,
                            Object current, Consumer<Object> changed, Runnable inherit) {
        if (activity == null || anchor == null || option == null || changed == null)
            throw new IllegalArgumentException("Missing editor argument");
        GlassUi ui = new GlassUi(activity, anchor, reduced);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body = ui.vertical();
        ui.pad(body, 20, 20, 20, 16);
        TextView title = ui.heading(option.label == null || option.label.isEmpty() ? option.key : option.label);
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        ui.space(body, 12);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout fields = ui.vertical();
        scroll.addView(fields, new ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        boolean hasValue = current != null && current != JSONObject.NULL;
        String currentText0 = !hasValue ? "" : current instanceof List
                ? new JSONArray((List<?>) current).toString() : current.toString();
        String currentText = currentText0;
        boolean vector = option.isVector() || current instanceof JSONArray || current instanceof List;
        // Vectors are shown as plain text ("10" or "0.4, 0.4", G-code as is) instead of raw JSON whenever that is lossless.
        final JSONArray vectorValues = !vector || !hasValue ? null : current instanceof JSONArray ? (JSONArray) current
                : current instanceof List ? new JSONArray((List<?>) current) : parseArray(currentText);
        final boolean plainVector = vectorValues != null && plainVector(vectorValues);
        if (plainVector) currentText = joinVector(vectorValues);
        boolean numericScalar = !vector && ("coInt".equals(option.type) || "coFloat".equals(option.type)
                || "coPercent".equals(option.type) || "coFloatOrPercent".equals(option.type));
        boolean enumList = !vector && !option.enumValues.isEmpty() && option.enumComplete;
        final EditText[] input = new EditText[1];
        final RadioGroup[] choices = new RadioGroup[1];
        final RadioGroup[] units = new RadioGroup[1];
        final int trueId = View.generateViewId(), falseId = View.generateViewId();
        final int mmId = View.generateViewId(), percentId = View.generateViewId();
        final java.util.Map<Integer,String> enumIds = new java.util.LinkedHashMap<>();

        if (option.isBoolean()) {
            choices[0] = radioGroup(activity, false);
            choices[0].addView(radio(activity, ui, activity.getString(R.string.phase2_editor_on), trueId));
            choices[0].addView(radio(activity, ui, activity.getString(R.string.phase2_editor_off), falseId));
            if (hasValue) try { choices[0].check(option.booleanValue(current) ? trueId : falseId); }
            catch (IllegalArgumentException ignored) { /* Unknown is not a guessed boolean. */ }
            fields.addView(choices[0]);
        } else if (!vector && !option.enumValues.isEmpty() && option.enumComplete) {
            choices[0] = radioGroup(activity, false);
            for (int i = 0; i < option.enumValues.size(); i++) {
                String value = option.enumValues.get(i);
                int id = View.generateViewId();
                enumIds.put(id, value);
                choices[0].addView(radio(activity, ui, option.enumLabels.get(i), id));
                if (hasValue && value.equals(currentText)) choices[0].check(id);
            }
            fields.addView(choices[0]);
            // An unrecognised existing enum must remain editable, never silently replaced.
            if (hasValue && choices[0].getCheckedRadioButtonId() == -1) {
                input[0] = ui.edit(currentText, InputType.TYPE_CLASS_TEXT, false);
                input[0].setHint(R.string.phase2_editor_unknown_enum);
                fields.addView(input[0]);
            }
        } else {
            boolean numeric = numericScalar;
            boolean multiline = vector || !numeric;
            String initial = currentText;
            if ("coFloatOrPercent".equals(option.type) && initial.endsWith("%"))
                initial = initial.substring(0, initial.length() - 1).trim();
            int kind = numeric ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            input[0] = ui.edit(initial, kind, multiline);
            input[0].setHint(vector && !plainVector ? activity.getString(R.string.phase2_editor_json_hint) : activity.getString(R.string.phase2_editor_value));
            input[0].setContentDescription(activity.getString(R.string.phase2_editor_value));
            input[0].setMinHeight(ui.dp(ui.tokens.minTouch));
            if (numeric) input[0].setGravity(Gravity.CENTER);
            if (multiline) input[0].setGravity(Gravity.TOP | Gravity.START);
            fields.addView(input[0], new LinearLayout.LayoutParams(-1, -2));
            if (vector) {
                ui.space(fields, 8);
                fields.addView(ui.text(plainVector ? (vectorValues.length() > 1 ? com.keplerslicer.app.L.t("Значение для каждого экструдера — через запятую.") : com.keplerslicer.app.L.t("Значение проверяется ядром при сохранении.")) : activity.getString(R.string.phase2_editor_vector_validation), 12, 400, ui.tokens.textSecondary));
            }
            if ("coFloatOrPercent".equals(option.type)) {
                ui.space(fields, 8);
                units[0] = radioGroup(activity, true);
                units[0].addView(radio(activity, ui, activity.getString(R.string.phase2_editor_unit_mm), mmId));
                units[0].addView(radio(activity, ui, activity.getString(R.string.phase2_editor_unit_percent), percentId));
                if (hasValue) units[0].check(currentText.endsWith("%") ? percentId : mmId);
                fields.addView(units[0]);
            }
        }
        TextView error = ui.text("", 13, 500, ui.tokens.textPrimary);
        error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        fields.addView(error, new LinearLayout.LayoutParams(-1, -2));
        if (inherit != null) {
            ui.space(body, 8);
            body.addView(ui.action(activity.getString(R.string.phase2_editor_inherit), false, () -> {
                inherit.run(); dialog.dismiss();
            }), new LinearLayout.LayoutParams(-1, ui.dp(48)));
        }
        ui.space(body, 8);
        LinearLayout actions = ui.horizontal();
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ui.dp(48), 1);
        left.rightMargin = ui.dp(8);
        actions.addView(ui.action(activity.getString(R.string.phase2_editor_cancel), false, dialog::dismiss), left);
        actions.addView(ui.action(activity.getString(R.string.phase2_editor_save), true, () -> {
            try {
                Object value;
                if (option.isBoolean()) {
                    int id = choices[0].getCheckedRadioButtonId();
                    if (id == -1) throw new IllegalArgumentException(activity.getString(R.string.phase2_editor_choose_value));
                    value = option.parseInput(id == trueId ? "1" : "0", current);
                } else if (choices[0] != null) {
                    int id = choices[0].getCheckedRadioButtonId();
                    String selected = enumIds.get(id);
                    if (selected == null && input[0] != null) selected = input[0].getText().toString();
                    if (selected == null) throw new IllegalArgumentException(activity.getString(R.string.phase2_editor_choose_value));
                    value = option.parseInput(selected, current);
                } else {
                    String text = input[0].getText().toString();
                    Object validationCurrent = current;
                    if (units[0] != null) {
                        int id = units[0].getCheckedRadioButtonId();
                        if (id == -1) throw new IllegalArgumentException(activity.getString(R.string.phase2_editor_choose_unit));
                        text = text.trim();
                        if (id == percentId) text += "%";
                        // parseInput otherwise preserves the previous percent suffix.
                        if (id == mmId && current instanceof String && ((String) current).endsWith("%"))
                            validationCurrent = ((String) current).substring(0, ((String) current).length()-1);
                    }
                    if (plainVector) text = splitVector(text, vectorValues).toString();
                    value = option.parseInput(text, validationCurrent);
                }
                changed.accept(value);
                dialog.dismiss();
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage() == null ? activity.getString(R.string.phase2_editor_input_error) : ex.getMessage());
                error.setVisibility(View.VISIBLE);
            }
        }), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        body.addView(actions);
        View surface = ui.surface(body, ui.tokens.cardRadius);
        dialog.setContentView(surface);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);window.setWindowAnimations(com.keplerslicer.app.R.style.KeplerSheetAnim);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes(); attributes.dimAmount = .45f; window.setAttributes(attributes);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (window != null) {
            int preferredDp = numericScalar || option.isBoolean() ? 260
                    : enumList ? Math.max(260, Math.min(720, 180 + option.enumValues.size() * 44))
                    : vector ? 440 : 480;
            AdaptiveHeight sizing = new AdaptiveHeight(activity, anchor, surface, window, ui.dp(preferredDp));
            sizing.attach();
            dialog.setOnDismissListener(ignored -> sizing.detach());
        }

    }

    /** Keep the bottom actions above the IME while only the editable content scrolls. */
    private static final class AdaptiveHeight {
        private final Activity activity;private final View anchor,surface;private final Window window;private final int preferred;
        private int lastHeight=-1;private boolean detached;
        private final android.view.ViewTreeObserver.OnGlobalLayoutListener layout=this::update;
        AdaptiveHeight(Activity activity,View anchor,View surface,Window window,int preferred){this.activity=activity;this.anchor=anchor;this.surface=surface;this.window=window;this.preferred=preferred;}
        void attach(){surface.getViewTreeObserver().addOnGlobalLayoutListener(layout);
            if(Build.VERSION.SDK_INT>=30)Insets30.listen(surface,this::update);
            surface.post(this::update);
        }
        void detach(){detached=true;if(surface.getViewTreeObserver().isAlive())surface.getViewTreeObserver().removeOnGlobalLayoutListener(layout);if(Build.VERSION.SDK_INT>=30)Insets30.stop(surface);}
        private void update(){if(detached||!surface.isAttachedToWindow())return;
            Rect visible=new Rect();anchor.getWindowVisibleDisplayFrame(visible);int available=visible.height();
            if(available<=0)available=activity.getResources().getDisplayMetrics().heightPixels;
            if(Build.VERSION.SDK_INT>=30)available=Math.min(available,Insets30.available(activity,anchor,surface));
            int desired=Math.max(1,Math.min(preferred,(int)(Math.max(1,available)*.8f)));
            if(desired!=lastHeight){lastHeight=desired;window.setLayout(-1,desired);}
            // ADJUST_RESIZE is not reliable on every edge-to-edge window. If the actual
            // dialog still overlaps the keyboard, lift it by the measured overlap.
            int imeTop=Build.VERSION.SDK_INT>=30?Insets30.imeTop(activity,anchor,surface):-1;
            if(Build.VERSION.SDK_INT<30){int screen=activity.getResources().getDisplayMetrics().heightPixels;
                if(screen-visible.bottom>Math.round(100*activity.getResources().getDisplayMetrics().density))imeTop=visible.bottom;}
            WindowManager.LayoutParams attributes=window.getAttributes();int offset=attributes.y;
            if(imeTop<0){if(offset!=0){attributes.y=0;window.setAttributes(attributes);}}
            else if(surface.getHeight()>0){int[] position=new int[2];surface.getLocationOnScreen(position);
                int correction=position[1]+surface.getHeight()-imeTop;int adjusted=Math.max(0,offset+correction);
                if(adjusted!=offset){attributes.y=adjusted;window.setAttributes(attributes);}}
        }
    }
    @android.annotation.TargetApi(30)
    private static final class Insets30 {
        static void listen(View surface,Runnable update){surface.setOnApplyWindowInsetsListener((view,insets)->{view.post(update);return insets;});surface.requestApplyInsets();}
        static void stop(View surface){surface.setOnApplyWindowInsetsListener(null);}
        static int imeTop(Activity activity,View anchor,View surface){
            WindowManager manager=(WindowManager)activity.getSystemService(Activity.WINDOW_SERVICE);int bottom=0;
            android.view.WindowInsets[] sources={anchor.getRootWindowInsets(),surface.getRootWindowInsets()};
            for(android.view.WindowInsets insets:sources)if(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime()))bottom=Math.max(bottom,insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);
            return bottom>0?manager.getCurrentWindowMetrics().getBounds().bottom-bottom:-1;
        }
        static int available(Activity activity,View anchor,View surface){
            WindowManager manager=(WindowManager)activity.getSystemService(Activity.WINDOW_SERVICE);
            int height=manager.getCurrentWindowMetrics().getBounds().height();int top=0,bottom=0;
            android.view.WindowInsets[] sources={anchor.getRootWindowInsets(),surface.getRootWindowInsets()};
            for(android.view.WindowInsets insets:sources)if(insets!=null){
                android.graphics.Insets bars=insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
                top=Math.max(top,bars.top);bottom=Math.max(bottom,bars.bottom);
                if(insets.isVisible(android.view.WindowInsets.Type.ime()))bottom=Math.max(bottom,insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);
            }
            return Math.max(1,height-top-bottom);
        }
    }

    public static void help(Activity activity, View anchor, boolean reduced, SettingsSchema.Option option) {
        String unknown = activity.getString(R.string.phase2_editor_details_unknown);
        StringBuilder details = new StringBuilder();
        if (option.tooltip != null && !option.tooltip.isEmpty()) details.append(option.tooltip).append("\n\n");
        details.append(activity.getString(R.string.phase2_editor_details_key, option.key));
        details.append("\n").append(activity.getString(R.string.phase2_editor_details_type, option.type == null ? unknown : option.type));
        if (option.min != null) details.append("\n").append(activity.getString(R.string.phase2_editor_details_min, String.valueOf(option.min)));
        if (option.max != null) details.append("\n").append(activity.getString(R.string.phase2_editor_details_max, String.valueOf(option.max)));
        if (!option.sourceReferences.isEmpty()) {
            details.append("\n\n").append(activity.getString(R.string.phase2_editor_details_sources));
            for (String source : option.sourceReferences) details.append("\n").append(source);
        }
        GlassBottomSheet.info(activity, anchor, reduced,
                option.label == null || option.label.isEmpty() ? activity.getString(R.string.phase2_editor_details_title) : option.label,
                details.toString());
    }
    private static RadioGroup radioGroup(Activity context, boolean horizontal) {
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(horizontal ? RadioGroup.HORIZONTAL : RadioGroup.VERTICAL);
        return group;
    }
    private static RadioButton radio(Activity context, GlassUi ui, String label, int id) {
        RadioButton button = new RadioButton(context);
        button.setId(id); button.setText(label); button.setTextColor(ui.tokens.textPrimary);
        button.setButtonTintList(ColorStateList.valueOf(ui.tokens.textPrimary));
        button.setTextSize(ui.tokens.bodySize); button.setMinHeight(ui.dp(ui.tokens.minTouch));
        GlassTypography.apply(button, 400);
        return button;
    }

    private static JSONArray parseArray(String text) {
        try { return new JSONArray(text); } catch (org.json.JSONException e) { return null; }
    }
    /** One value, or several short values without commas/newlines. */
    private static boolean plainVector(JSONArray values) {
        if (values.length() == 0) return false;
        for (int i = 0; i < values.length(); i++) {
            Object v = values.opt(i);
            if (!(v instanceof String) && !(v instanceof Number) && !(v instanceof Boolean)) return false;
            if (values.length() > 1 && (String.valueOf(v).contains(",") || String.valueOf(v).contains("\n"))) return false;
        }
        return true;
    }
    private static String joinVector(JSONArray values) {
        if (values.length() == 1) return String.valueOf(values.opt(0));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length(); i++) { if (i > 0) b.append(", "); b.append(values.opt(i)); }
        return b.toString();
    }
    private static JSONArray splitVector(String text, JSONArray original) {
        JSONArray out = new JSONArray();
        if (original.length() == 1) { out.put(text); return out; }
        for (String part : text.split(",", -1)) out.put(part.trim());
        return out;
    }
}
