package com.zj.screenrecorder.recorder;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.text.format.DateUtils;

import static android.os.Build.VERSION_CODES.O;

import com.zj.screenrecorder.R;

/**
 * @author Zjj
 * @version 2022/4/13
 */
class Notifications extends ContextWrapper {
    private static final int id = 0x1fff;
    private static final String CHANNEL_ID = "Recording";
    private static final String CHANNEL_NAME = "Screen Recorder Notifications";

    private long mLastFiredTime = 0;
    private NotificationManager mManager;
    private Notification.Action mStopAction;
    private Notification.Builder mBuilder;

    Notifications(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= O) {
            createNotificationChannel();
        }
    }

    public void recording(long timeMs) {
        if (SystemClock.elapsedRealtime() - mLastFiredTime < 1000) {
            return;
        }
        String content = getString(R.string.length_video) + " " + DateUtils.formatElapsedTime(timeMs / 1000);
        Notification notification = getBuilder().setContentText(content).build();
        getNotificationManager().notify(id, notification);
        mLastFiredTime = SystemClock.elapsedRealtime();
    }

    private Notification.Builder getBuilder() {
        if (mBuilder == null) {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= O) {
                builder = new Notification.Builder(this, CHANNEL_ID).setUsesChronometer(true);
            } else {
                builder = new Notification.Builder(this);
            }
            builder.setContentTitle(getString(R.string.recording)).setOngoing(true).setLocalOnly(true).setOnlyAlertOnce(true).addAction(stopAction()).setWhen(System.currentTimeMillis()).setSmallIcon(R.drawable.ic_stat_recording);
            mBuilder = builder;
        }
        return mBuilder;
    }

    @TargetApi(O)
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getNotificationManager().createNotificationChannel(channel);
    }

    private Notification.Action stopAction() {
        if (mStopAction == null) {
            Intent intent = new Intent(RecorderBuilder.ACTION_STOP).setPackage(getPackageName());
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, intent, flags);
            mStopAction = new Notification.Action(android.R.drawable.ic_media_pause, getString(R.string.stop), pendingIntent);
        }
        return mStopAction;
    }

    void clear() {
        mLastFiredTime = 0;
        mBuilder = null;
        mStopAction = null;
        getNotificationManager().cancelAll();
    }

    NotificationManager getNotificationManager() {
        if (mManager == null) {
            mManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mManager;
    }
}
