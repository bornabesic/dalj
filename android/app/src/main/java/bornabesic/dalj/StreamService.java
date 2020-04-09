package bornabesic.dalj;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import static java.lang.Thread.sleep;

public class StreamService extends Service {

    private Thread thread;
    private boolean running = false;

    @Override
    public void onCreate() {

    }

    @Override
    public void onDestroy() {
        Log.d("StreamService", "onDestroy()");
        running = false;
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (running) {
            Log.d(this.getClass().toString(), "Already running.");
            return START_NOT_STICKY;
        }

        NotificationChannel channel = new NotificationChannel("10101", "Dalj.StreamService", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        startForeground(10101, new NotificationCompat.Builder(this, "10101")
                .setContentTitle("Dalj")
                .setContentText("Streaming...")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        );

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection projection = manager.getMediaProjection(Activity.RESULT_OK, intent);

                VirtualDisplay display = projection.createVirtualDisplay(
                        "Dalj",
                        100, 800,
                        20, 0,
                        null, null, null
                );

                running = true;
                int i = 0;
                while (running) {
                    Log.d("StreamService", "TICK " + Integer.valueOf(i).toString());
                    i++;
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                stopSelf();
            }
        });
        thread.start();

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
