package com.merabills.videorecorder;

import static com.merabills.videorecorder.MainActivity.KEY_DATA;
import static com.merabills.videorecorder.MainActivity.KEY_RESULT_CODE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver that listens for screen recording commands (start/stop)
 * sent via broadcast intents. It starts or stops the ScreenRecorderService accordingly.
 */

public class ScreenRecorderBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            Log.e(TAG, "Received null intent");
            return;
        }

        final String action = intent.getStringExtra(Constants.KEY_ACTION);
        final String fileName = intent.getStringExtra(Constants.KEY_FILE_NAME);

        if (action == null || fileName == null) {
            Log.e(TAG, "Missing action or file name in broadcast");
            return;
        }

        if (MainActivity.data == null || MainActivity.resultCode == 0) {
            Log.e(TAG, "MediaProjection permission data not initialized");
            return;
        }

        // Create an intent for the screen recording service
        final Intent serviceIntent = new Intent(context, ScreenRecorderService.class);
        serviceIntent.putExtra(Constants.KEY_ACTION, action);
        serviceIntent.putExtra(Constants.KEY_FILE_NAME, fileName);
        serviceIntent.putExtra(KEY_RESULT_CODE, MainActivity.resultCode);
        serviceIntent.putExtra(KEY_DATA, MainActivity.data);

        // Start the screen recording service as a foreground service
        try {
            context.startForegroundService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScreenRecorderService", e);
        }
    }
    private static final String TAG = "ScreenRecorderReceiver";
}
