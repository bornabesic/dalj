package bornabesic.dalj;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class StreamService extends Service {

    private Thread thread;
    private boolean running = false;
    private final String CHANNEL_ID = "bornabesic.dalj";

    @Override
    public void onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("StreamService", "Creating notification channel...");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Dalj.StreamService", NotificationManager.IMPORTANCE_DEFAULT));
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Dalj")
                    .setContentText("Streaming...")
                    .setChannelId(CHANNEL_ID)
                    .build();
            startForeground(0, notification);
            Log.d(this.getClass().toString(), "Started in foreground.");
        }
        else {
            startForeground(0, new Notification());
            Log.d(this.getClass().toString(), "Started in foreground.");
        }
    }

    @Override
    public void onDestroy() {
        Log.d("StreamService", "onDestroy()");
        running = false;
        super.onDestroy();
    }


    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (running) {
            Log.d(this.getClass().toString(), "Already running.");
            return START_NOT_STICKY;
        }

        DisplayMetrics metrics = this.getResources().getDisplayMetrics();
        final Integer screenWidth = metrics.widthPixels;
        final Integer screenHeight = metrics.heightPixels;
        final Integer screenDpi = metrics.densityDpi;

        final String externalStoragePath = Environment.getExternalStorageDirectory().toString();

        Log.d(StreamService.class.toString(), "Width: " + screenWidth.toString());
        Log.d(StreamService.class.toString(), "Height: " + screenHeight.toString());
        Log.d(StreamService.class.toString(), "DPI: " + screenDpi.toString());
        Log.d(StreamService.class.toString(), externalStoragePath);

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection projection = manager.getMediaProjection(Activity.RESULT_OK, intent);

                final Integer SIZE = Math.max(screenWidth, screenHeight);
                // NOTE For some retarded reason, ImageReader requires width == height
                ImageReader reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 60);
                assert reader.getSurface() != null;

                Log.d("StreamService", reader.getSurface().toString());

                VirtualDisplay display = projection.createVirtualDisplay(
                        "Dalj",
                        screenWidth, screenHeight,
                        screenDpi, 0,
                        reader.getSurface(),
                        null, null
                );

                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket();
                } catch (SocketException e) {
                    e.printStackTrace();
                }

                running = true;
                Image image;
                long start = -1, end = -1;
                // FileOutputStream out = null;
                while (running) {
                    start = end;
                    image = reader.acquireLatestImage();
                    if (image == null) continue;
                    end = System.nanoTime();

                    if (start != -1) {
                        long nspf = end - start;
                        double spf = nspf / 1000000000.0;
                        double fps = 1 / spf;
                        Log.d("StreamService", "FPS: " + Double.valueOf(fps).toString());
                    }

                    Image.Plane planes[] = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();

                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * screenWidth;

                    Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    Log.d(StreamService.class.toString(), bitmap.getWidth() + " x " + bitmap.getHeight());

/*                    if (out == null) {
                        try {
                            out = new FileOutputStream(externalStoragePath + "/debug.jpg");
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                            out.close();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }*/

                    image.close();
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
