package io.causelane.cosmic;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Delivers scheduled local reminders independently of the launcher activity lifecycle. */
public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "nexora_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        String message = intent == null ? "Your scheduled reminder" : intent.getStringExtra("message");
        if (message == null || message.trim().isEmpty()) message = "Your scheduled reminder";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Nexora reminders", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(context, CosmicHomeActivity.class); open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, (int)(System.currentTimeMillis() & 0x7fffffff), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Nexora reminder")
                .setContentText(message).setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true).setContentIntent(tap).build();
        manager.notify((int)(System.currentTimeMillis() & 0x7fffffff), notification);
    }
}