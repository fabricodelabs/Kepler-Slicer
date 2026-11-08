package com.keplerslicer.app;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;

/**
 * Tablet mode follows desktop OrcaSlicer: settings editors, profile lists, connections, export and Preferences are
 * dialog windows over the workspace (centred, dimmed behind, close button), not full-screen phone screens.
 */
final class DesktopWindow {
    private DesktopWindow() {}

    /** Screens shown as windows; the workspace (MainActivity) and the Device workspace stay full screen. */
    static boolean windowed(Activity a) { return com.keplerslicer.app.glass.Desktop.windowed(a); }

    /** Lists (profile pickers) get a narrower window than editors. */
    private static boolean narrow(Activity a) {
        String n = a.getClass().getSimpleName();
        return n.endsWith("PickerActivity") || n.equals("CloudPrintLoginActivity") || n.equals("SettingsTransferActivity");
    }

    /** Called while the activity is created, before its content. */
    static void apply(Activity a) {
        if (!windowed(a)) return;
        a.setTranslucent(true);
        Window w = a.getWindow();
        float d = a.getResources().getDisplayMetrics().density;
        android.graphics.Rect bounds = a.getWindowManager().getCurrentWindowMetrics().getBounds();
        int width = Math.min(Math.round((narrow(a) ? 760 : 1180) * d), bounds.width() - Math.round(96 * d));
        int height = bounds.height() - Math.round(96 * d);
        w.setLayout(width, height);
        w.setGravity(Gravity.CENTER);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams p = w.getAttributes(); p.dimAmount = .5f; w.setAttributes(p);
        w.setBackgroundDrawable(com.keplerslicer.app.glass.Desktop.surface(a, 16));
        w.setWindowAnimations(0);
    }

    /** After the content exists: the window clips its content to the rounded surface. */
    static void afterContent(Activity a) {
        if (!windowed(a)) return;
        View decor = a.getWindow().getDecorView();
        decor.setOutlineProvider(ViewOutlineProvider.BACKGROUND); decor.setClipToOutline(true);
    }
}
