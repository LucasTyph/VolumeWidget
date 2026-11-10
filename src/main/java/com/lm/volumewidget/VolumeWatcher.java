package com.lm.volumewidget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;

/**
 * Keeps the widgets in step with volume changes made outside the widget (hardware keys,
 * the system volume panel, other apps), without a permanently running service:
 *
 * - While the app's process is alive, a broadcast receiver redraws instantly.
 * - When it is not, a job triggered by changes to the system settings (where Android saves
 *   each volume shortly after it changes) restarts the process to redraw.
 */
public class VolumeWatcher extends JobService {
    static final int JOB_ID = 1;
    /** Not in the public SDK, but sent by every Android version on any volume change. */
    static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    static final String MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION";

    /** Extra callback run on each volume change; used by the settings screen's preview. */
    static Runnable onChange;
    private static boolean listening;

    /** Starts watching if on, stops the background job if not. Safe to call repeatedly. */
    static void watch(Context c, boolean on) {
        Context app = c.getApplicationContext();
        JobScheduler js = app.getSystemService(JobScheduler.class);
        if (!on) {
            js.cancel(JOB_ID);
            return;
        }
        if (!listening) {
            listening = true;
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent i) {
                    VolumeWidget.updateAll(ctx);
                    if (onChange != null) onChange.run();
                }
            };
            IntentFilter f = new IntentFilter(VOLUME_CHANGED);
            f.addAction(MUTE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33)
                app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
            else
                app.registerReceiver(r, f);
        }
        if (js.getPendingJob(JOB_ID) == null) schedule(app);
    }

    private static void schedule(Context app) {
        app.getSystemService(JobScheduler.class).schedule(
                new JobInfo.Builder(JOB_ID, new ComponentName(app, VolumeWatcher.class))
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(
                                Settings.System.CONTENT_URI,
                                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                        .setTriggerContentUpdateDelay(200)
                        .setTriggerContentMaxDelay(1000)
                        .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        VolumeWidget.updateAll(this);
        // A content-triggered job fires only once, so arm it again for the next change.
        schedule(getApplicationContext());
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
