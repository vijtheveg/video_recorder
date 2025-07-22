package com.merabills.videorecorder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground service responsible for screen and audio recording using MediaProjection API.
 */
public class ScreenRecorderService extends Service {

    private static final String CHANNEL_ID = "ScreenRecorderChannel";
    private static final String TAG = "ScreenRecorderService";
    private static final String EXTENSION_MP4 = ".mp4";
    private static final String MIME_TYPE_MP4 = "video/mp4";
    private static final String NAME_RECORDING_FOLDER = "/ScreenRecords";

    private MediaProjection mediaProjection;
    private ScreenMicRecorder screenMicRecorder;
    private InternalAudioRecorder internalAudioRecorder;

    private MuxerCoordinator muxerCoordinator;


    private File outputFile;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @RequiresPermission(allOf = {"android.permission.RECORD_AUDIO"})
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        startForeground(1, createNotification());

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        int resultCode = MainActivity.resultCode;
        Intent data = MainActivity.data;

        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection != null) {
            startRecording();
        }

        return START_NOT_STICKY;
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startRecording() {
        try {
            final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            final String fileName = "recording_" + timestamp;

            Uri videoUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName + EXTENSION_MP4);
                values.put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE_MP4);
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + NAME_RECORDING_FOLDER);

                videoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                assert videoUri != null;

                OutputStream out = getContentResolver().openOutputStream(videoUri);
                if (out != null) out.close();

                outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES + NAME_RECORDING_FOLDER), fileName + EXTENSION_MP4);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Recordings");
                if (!dir.exists()) dir.mkdirs();
                outputFile = new File(dir, fileName + EXTENSION_MP4);
            }

            MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            muxerCoordinator = new MuxerCoordinator(muxer, 2); // video + audio

            screenMicRecorder = new ScreenMicRecorder(mediaProjection, muxerCoordinator);
            screenMicRecorder.prepare();

            internalAudioRecorder = new InternalAudioRecorder(mediaProjection, muxerCoordinator);
            internalAudioRecorder.prepare();

            screenMicRecorder.start();
            internalAudioRecorder.start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            stopSelf();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service stopping");
        if (screenMicRecorder != null) screenMicRecorder.stop();
        if (internalAudioRecorder != null) internalAudioRecorder.stop();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && outputFile != null) {
            final Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(outputFile));
            sendBroadcast(mediaScanIntent);
        }
        if (muxerCoordinator != null) {
            muxerCoordinator.stopMuxer();
        }

        super.onDestroy();
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Recorder",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen recording in progress")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}