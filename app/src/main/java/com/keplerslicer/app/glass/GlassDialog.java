package com.keplerslicer.app.glass;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.keplerslicer.app.R;

/**
 * Restyles a platform AlertDialog into Glass UI v3 after it is laid out: frosted rounded card, Manrope type, a white
 * primary pill and glass secondary pills. The dialog keeps its behaviour, so callers may still use getButton().
 */
public final class GlassDialog {
    private GlassDialog() {}

    public static AlertDialog style(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        View decor = window.getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) == null && dialog.findViewById(android.R.id.message) == null) return;
                decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                apply(dialog);
            }
        });
        return dialog;
    }

    private static void apply(AlertDialog dialog) {
        Context c = dialog.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        Window window = dialog.getWindow();
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xF22E323A); card.setCornerRadius(28 * d); card.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        window.setBackgroundDrawable(new InsetDrawable(card, Math.round(16 * d), 0, Math.round(16 * d), 0));
        Typeface bold = font(c, R.font.manrope_bold), semibold = font(c, R.font.manrope_semibold), regular = font(c, R.font.manrope_medium);
        int titleId = c.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) { title.setTypeface(bold); title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20); title.setTextColor(0xFFFFFFFF); title.setLetterSpacing(-0.01f); }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) { message.setTypeface(regular); message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); message.setTextColor(0xE0FFFFFF); message.setLineSpacing(4 * d, 1f); }
        styleFields(dialog.getWindow().getDecorView(), regular, d);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE), negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE), neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        pill(positive, true, semibold, d); pill(negative, false, semibold, d); pill(neutral, false, semibold, d);
        if (positive != null && positive.getParent() instanceof View) {
            View bar = (View) positive.getParent(); bar.setPadding(Math.round(16 * d), Math.round(8 * d), Math.round(16 * d), Math.round(16 * d));
            bar.setMinimumHeight(Math.round(68 * d));
            for (View p = bar; p != null && p.getParent() instanceof ViewGroup; p = (View) p.getParent()) { ((ViewGroup) p.getParent()).setClipChildren(false); if (p.getParent() == window.getDecorView()) break; }
            bar.requestLayout();
        }
    }

    private static void pill(Button b, boolean primary, Typeface face, float d) {
        if (b == null || b.getVisibility() != View.VISIBLE) return;
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(22 * d);
        shape.setColor(primary ? 0xFFFFFFFF : 0x1FFFFFFF);
        if (!primary) shape.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x22000000 : 0x33FFFFFF), shape, null));
        b.setAllCaps(false); b.setTypeface(face); b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{primary ? 0x661B1C1B : 0x66FFFFFF, primary ? 0xFF1B1C1B : 0xFFFFFFFF}));
        b.setMinHeight(Math.round(44 * d)); b.setMinimumHeight(Math.round(44 * d));
        b.setPadding(Math.round(20 * d), 0, Math.round(20 * d), 0);
        b.setStateListAnimator(null); b.setElevation(0);
        ViewGroup.LayoutParams lp = b.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).setMargins(Math.round(4 * d), 0, Math.round(4 * d), 0); b.setLayoutParams(lp); }
    }

    /** Text fields and checkboxes inside a custom dialog view. */
    private static void styleFields(View v, Typeface face, float d) {
        if (v instanceof EditText) {
            EditText e = (EditText) v;
            GradientDrawable field = new GradientDrawable(); field.setCornerRadius(14 * d); field.setColor(0x24000000); field.setStroke(Math.max(1, Math.round(d)), 0x29FFFFFF);
            e.setBackground(field); e.setTypeface(face); e.setTextColor(0xFFFFFFFF); e.setHintTextColor(0x8CFFFFFF);
            e.setPadding(Math.round(14 * d), Math.round(10 * d), Math.round(14 * d), Math.round(10 * d));
            ViewGroup.LayoutParams lp = e.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).topMargin = Math.round(6 * d); e.setLayoutParams(lp); }
            return;
        }
        if (v instanceof android.widget.CompoundButton && !(v instanceof android.widget.Switch)) ((android.widget.CompoundButton) v).setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFF5EE0C8, 0x8CFFFFFF}));
        if (v instanceof TextView && !(v instanceof Button && !(v instanceof android.widget.CompoundButton)) && v.getId() == View.NO_ID) ((TextView) v).setTypeface(face);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) styleFields(g.getChildAt(i), face, d); }
    }

    private static Typeface font(Context c, int id) {
        try { return c.getResources().getFont(id); } catch (RuntimeException e) { return Typeface.DEFAULT_BOLD; }
    }
}
