package com.keplerslicer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Short foreground service that keeps the app alive while the user signs in in the browser (at most 3 minutes). */
public final class LoginKeepAlive extends Service {
    private static final String CHANNEL = "cloud_login";

    static void start(Context c) { try { c.startForegroundService(new Intent(c, LoginKeepAlive.class)); } catch (RuntimeException ignored) { } }
    static void stop(Context c) { try { c.stopService(new Intent(c, LoginKeepAlive.class)); } catch (RuntimeException ignored) { } }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, L.t("Вход в облако"), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.kv3_link).setContentTitle("Kepler Slicer")
            .setContentText(L.t("Ожидаем вход в браузере…")).setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(7001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
            else startForeground(7001, n);
        } catch (RuntimeException e) { stopSelf(); }
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId) { stopSelf(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
