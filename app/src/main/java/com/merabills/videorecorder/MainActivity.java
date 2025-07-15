package com.merabills.videorecorder;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * MainActivity handles user interaction to start screen recording using MediaProjection,
 * and launches the MeraBills app after a short delay.
 */

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // If the user granted screen capture permission
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        final Intent data = result.getData();

                        // Create an intent for the ScreenRecorderService
                        final Intent serviceIntent = new Intent(this, ScreenRecorderService.class);
                        serviceIntent.putExtra(KEY_RESULT_CODE, result.getResultCode());
                        serviceIntent.putExtra(KEY_DATA, data);

                        // Start service based on Android version
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            startForegroundService(serviceIntent);
                        else
                            startService(serviceIntent);

                        // Launch MeraBills app after short delay
                        new Handler().postDelayed(this::launchMeraBills, 2000);
                    } else {
                        Toast.makeText(
                                this,
                                "Screen capture permission denied",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });

        findViewById(R.id.button_start_recording)
                .setOnClickListener(v -> launchMeraBillsAndStartRecording());
    }

    /**
     * Launches MediaProjection screen capture intent and starts screen recording service
     * on user consent. Then launches the MeraBills app after a short delay.
     */
    private void launchMeraBillsAndStartRecording() {
        // Check and request RECORD_AUDIO permission if not already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    100);
        }

        // Create and launch screen capture intent using MediaProjectionManager
        final MediaProjectionManager mpm
                = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        final Intent screenCaptureIntent = mpm.createScreenCaptureIntent();
        screenCaptureLauncher.launch(screenCaptureIntent);
    }

    /**
     * Launches the MeraBills app by explicitly specifying its package and main activity.
     */
    private void launchMeraBills() {
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                NAME_MERA_BILLS_PACKAGE,
                NAME_MERA_BILLS_PACKAGE + NAME_INTENT_CLASS)
        );
        startActivity(intent);
    }

    public static final String KEY_DATA = "data";
    public static final String KEY_RESULT_CODE = "resultCode";

    private static final String NAME_MERA_BILLS_PACKAGE = "com.merabills.merchant_app_android";

    private static final String NAME_INTENT_CLASS = ".activities.MainActivity";

    private ActivityResultLauncher<Intent> screenCaptureLauncher;
}