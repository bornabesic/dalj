package bornabesic.dalj;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private String appName;
    private final int PERMISSION_CODE = 1;
    private Intent serviceIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(appName, "onCreate()");

        appName = getString(R.string.app_name);

        setContentView(R.layout.activity_main);
        final Button startButton = findViewById(R.id.btn_start);
        final Button stopButton = findViewById(R.id.btn_stop);

        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                startActivityForResult(manager.createScreenCaptureIntent(), PERMISSION_CODE);
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceIntent != null) {
                    stopService(serviceIntent);
                    Toast.makeText(MainActivity.this, "Service stopped.", Toast.LENGTH_SHORT).show();
                    serviceIntent = null;
                }
                else Toast.makeText(MainActivity.this, "The service is not running.", Toast.LENGTH_SHORT).show();

            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(appName, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            return;
        }

        Log.d(appName, Integer.valueOf(resultCode).toString());

        EditText ipEdit0 = findViewById(R.id.ip0);
        EditText ipEdit1 = findViewById(R.id.ip1);
        EditText ipEdit2 = findViewById(R.id.ip2);
        EditText ipEdit3 = findViewById(R.id.ip3);

        EditText portEdit = findViewById(R.id.port);
        String ip = ipEdit0.getText().toString() + "." + ipEdit1.getText().toString() + "." + ipEdit2.getText().toString() + "." + ipEdit3.getText().toString();
        String port = portEdit.getText().toString();

        serviceIntent = (Intent) data.clone();

        try {
            serviceIntent.putExtra("ip", ip);
            serviceIntent.putExtra("port", Integer.valueOf(port));
        }
        catch (NumberFormatException e) {
            Toast.makeText(MainActivity.this, "Check the values!", Toast.LENGTH_SHORT).show();
            serviceIntent = null;
            return;
        }

        serviceIntent.setClass(MainActivity.this, StreamService.class);

        startService(serviceIntent);
        Toast.makeText(MainActivity.this, "Service started.", Toast.LENGTH_LONG).show();
        // moveTaskToBack(true);
    }
}
