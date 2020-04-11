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
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.TypedArrayUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class StreamService extends Service {

    private Thread thread;
    private boolean running = false;
    private final String CHANNEL_ID = "bornabesic.dalj";
    final int PACKET_SIZE = 24 * 1000;

    @Override
    public void onCreate() {
        Notification notification = new Notification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("StreamService", "Creating notification channel...");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Dalj.StreamService", NotificationManager.IMPORTANCE_DEFAULT));
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setChannelId(CHANNEL_ID)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentTitle("Dalj")
                    .setContentText("Streaming...")
                    .build();
            Log.d(this.getClass().toString(), "Started in foreground (1).");
        }
        else {
            Log.d(this.getClass().toString(), "Started in foreground (2).");
        }
        startForeground(1, notification);
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

        // final String externalStoragePath = Environment.getExternalStorageDirectory().toString();

/*        Log.d(StreamService.class.toString(), "IP: " + ip);
        Log.d(StreamService.class.toString(), "Port:" + port);

        Log.d(StreamService.class.toString(), "Width: " + screenWidth.toString());
        Log.d(StreamService.class.toString(), "Height: " + screenHeight.toString());
        Log.d(StreamService.class.toString(), "DPI: " + screenDpi.toString());
        Log.d(StreamService.class.toString(), externalStoragePath);*/

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection projection = manager.getMediaProjection(Activity.RESULT_OK, intent);

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

                String ipString = intent.getStringExtra("ip");
                final Integer port = intent.getIntExtra("port", 10101);

                Log.d(StreamService.class.toString(), "IP: " + ipString);
                Log.d(StreamService.class.toString(), "Port: " + port);

                InetAddress ip = null;
                try {
                    ip = InetAddress.getByName(ipString);
                } catch (UnknownHostException e) {
                    System.exit(1);
                }


                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket();
                    socket.setSendBufferSize(PACKET_SIZE);
                    Log.d(StreamService.class.toString(), "Buffer size:" + socket.getSendBufferSize());
                } catch (SocketException e) {
                    e.printStackTrace();
                }

                ByteArrayOutputStream2 baos = new ByteArrayOutputStream2(PACKET_SIZE);

                running = true;
                Image image;
                long start = -1, end = -1;
                DatagramPacket packet = null;
                while (running) {
                    image = reader.acquireLatestImage();
                    if (image == null) continue;
                    start = end;
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

                    Bitmap bitmapSmall = Bitmap.createScaledBitmap(bitmap, screenWidth / 2, screenHeight / 2, true);

                    baos.reset();
                    bitmapSmall.compress(Bitmap.CompressFormat.JPEG, 10, baos);
                    Log.d(StreamService.class.toString(), "Bytes:" + baos.bytes.length);
                    assert baos.length == PACKET_SIZE;

                    packet = new DatagramPacket(baos.bytes, baos.length, ip, port);
                    try {
                        socket.send(packet);
                        Log.d(StreamService.class.toString(), "Packet sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

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
