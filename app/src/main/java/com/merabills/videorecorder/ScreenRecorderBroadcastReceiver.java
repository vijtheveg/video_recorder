package com.merabills.videorecorder;

import static com.merabills.videorecorder.MainActivity.KEY_DATA;
import static com.merabills.videorecorder.MainActivity.KEY_RESULT_CODE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * BroadcastReceiver that listens for screen recording commands (start/stop)
 * sent via broadcast intents. It starts or stops the ScreenRecorderService accordingly.
 */

public class ScreenRecorderBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // Extract action and file name from the broadcast intent
        final String action = intent.getStringExtra(KEY_ACTION);
        final String fileName = intent.getStringExtra(KEY_FILE_NAME);

        Log.d("RecordingCommandReceiver", "Received action: " + action + ", file: " + fileName);

        // Create an intent for the screen recording service
        final Intent serviceIntent = new Intent(context, ScreenRecorderService.class);
        serviceIntent.putExtra(KEY_ACTION, action);
        serviceIntent.putExtra(KEY_FILE_NAME, fileName);
        serviceIntent.putExtra(KEY_RESULT_CODE, MainActivity.resultCode);
        serviceIntent.putExtra(KEY_DATA, MainActivity.data);

        // Start or stop the service based on the action
        if (VALUE_START.equals(action)) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent);
            else
                context.startService(serviceIntent);
        } else {
            context.stopService(serviceIntent);
        }

    }

    public static final String KEY_ACTION = "action";
    public static final String KEY_FILE_NAME = "file_name";
    private static final String VALUE_START = "start";
}
