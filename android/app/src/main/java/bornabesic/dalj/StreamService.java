package bornabesic.dalj;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static java.net.StandardSocketOptions.SO_SNDBUF;

public class StreamService extends Service {

    private Thread thread;
    private boolean running = false;
    private final String CHANNEL_ID = "bornabesic.dalj";
    final int MAX_PACKET_SIZE = 62 * 1000;
    final int SIZE_DIVISOR = 2;
    final int DPI = 30;
    final int FPS_LIMIT = 60;
    final int USPF = 1000000 / FPS_LIMIT;
    final String MIME_TYPE = "video/avc";

    Integer screenWidth = null;
    Integer screenHeight = null;
    Integer screenDpi = null;

    MediaProjectionManager manager = null;
    MediaProjection projection = null;
    VirtualDisplay display = null;
    Intent intent = null;

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

    private static MediaCodecInfo findEncoder(String mimeType) {
        String found = "";
        MediaCodecInfo codecInfoFound = null;
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    codecInfoFound = codecInfo;
                    found += "\n" + codecInfoFound.getName();
                }
            }
        }
        MainActivity.showDialog("Codecs", found);
        return codecInfoFound;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (running) {
            Log.d(this.getClass().toString(), "Already running.");
            return START_NOT_STICKY;
        }

        this.intent = intent;

        // final Display display = ((WindowManager) StreamService.this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        thread = new Thread(new Runnable() {

            private void stream() throws IOException {
                DisplayMetrics metrics = StreamService.this.getResources().getDisplayMetrics();
                screenWidth = metrics.widthPixels;
                screenHeight = metrics.heightPixels;
                screenDpi = metrics.densityDpi;

                screenWidth /= SIZE_DIVISOR;
                screenHeight /= SIZE_DIVISOR;

                while (screenWidth % 2 != 0) screenWidth++;
                while (screenHeight % 2 != 0) screenHeight++;

                MainActivity.showDialog("Resolution", screenWidth + " x " + screenHeight);

                Log.d(StreamService.class.toString(), screenWidth + " x " + screenHeight + " @ " + screenDpi);

                manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                projection = manager.getMediaProjection(Activity.RESULT_OK, intent);

                MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, screenWidth, screenHeight);
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000);
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FPS_LIMIT);
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, -1);

                /*
                https://developer.samsung.com/forum/thread/exynos-avc-decoder-fails-to-decode-h264-on-s8-and-newer-devices/201/361542?boardName=SDK&startId=zzzzz~
                 */
                mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, FPS_LIMIT);
                mediaFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, FPS_LIMIT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mediaFormat.setInteger(MediaFormat.KEY_MAX_FPS_TO_ENCODER, FPS_LIMIT);
                }
                mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0); // Real-time
                mediaFormat.setInteger(MediaFormat.KEY_COMPLEXITY, 0);


                mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);

                mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, screenHeight);
                mediaFormat.setInteger(MediaFormat.KEY_WIDTH, screenWidth);

                // Log.d(StreamService.class.toString(), "Tile height: " + mediaFormat.getInteger(MediaFormat.KEY_TILE_HEIGHT));


                MediaCodecInfo encoderInfo = findEncoder(MIME_TYPE);
                Log.d(StreamService.class.toString(), encoderInfo.getName());

                MediaCodec encoder = null;
                encoder = MediaCodec.createByCodecName(encoderInfo.getName());
                encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

                Surface surface = encoder.createInputSurface();

                display = projection.createVirtualDisplay(
                        "Dalj",
                        screenWidth, screenHeight,
                        DPI, 0,
                        surface,
                        null, null
                );

                encoder.start();

                String ipString = intent.getStringExtra("ip");
                final Integer port = intent.getIntExtra("port", 10101);
                final Integer quality = intent.getIntExtra("quality", 10);

                Log.d(StreamService.class.toString(), "IP: " + ipString);
                Log.d(StreamService.class.toString(), "Port: " + port);
                Log.d(StreamService.class.toString(), "Quality: " + quality);

                InetAddress ip = InetAddress.getByName(ipString);

                DatagramChannel datagramChannel = null;
                InetSocketAddress inetSocketAddress = null;

                datagramChannel = DatagramChannel.open();
                inetSocketAddress = new InetSocketAddress(ip, port);
                datagramChannel.setOption(SO_SNDBUF, MAX_PACKET_SIZE);
                Log.d(StreamService.class.toString(), "Buffer size:" + datagramChannel.getOption(SO_SNDBUF));


                final int TIMEOUT_USEC = USPF / 2;
                running = true;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (running) {
                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    switch (encoderStatus) {
                        case  MediaCodec.INFO_TRY_AGAIN_LATER:
                            continue;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.d(StreamService.class.toString(), encoder.getOutputFormat().toString());
                            //stopSelf();
                           continue;
                    }

                    ByteBuffer encodedData = encoder.getOutputBuffer(encoderStatus);

                    // encodedData.position(info.offset);
                    // encodedData.limit(info.offset + info.size);

                    datagramChannel.send(encodedData, inetSocketAddress);
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }

                stopSelf();
            }

            @Override
            public void run() {
                try {
                    stream();
                } catch (final Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    String stackTrace = sw.toString();
                    MainActivity.showDialog("Exception", stackTrace);
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
