package com.merabills.videorecorder;

import static com.merabills.videorecorder.MainActivity.KEY_DATA;
import static com.merabills.videorecorder.MainActivity.KEY_RESULT_CODE;
import static com.merabills.videorecorder.ScreenRecorderBroadcastReceiver.KEY_FILE_NAME;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Foreground service responsible for screen and audio recording using MediaProjection API.
 * Triggered via broadcast and controlled by intent extras.
 */

public class ScreenRecorderService extends Service {

    /**
     * Entry point when the service is started via an intent.
     * Initializes screen recording with MediaProjection and MediaRecorder.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        final String fileName = intent.getStringExtra(KEY_FILE_NAME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else
            startForeground(1, createNotification());

        // Get required data from the original MediaProjection request
        final int resultCode = intent.getIntExtra(KEY_RESULT_CODE, Activity.RESULT_CANCELED);
        final Intent data = intent.getParcelableExtra(KEY_DATA);
        final MediaProjectionManager mpm
                = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        assert data != null;
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        startRecording(fileName);

        return START_NOT_STICKY;
    }

    /**
     * Configures MediaRecorder and starts screen + audio recording.
     */
    private void startRecording(String fileName) {
        try {

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            // Set output destination depending on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                // For Android 10+, store in scoped storage using MediaStore
                final ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName + EXTENSION_MP4);
                values.put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE_MP4);
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + NAME_RECORDING_FOLDER);
                final Uri videoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                assert videoUri != null;

                // Create and immediately close an OutputStream to register the file
                final OutputStream out = getContentResolver().openOutputStream(videoUri);
                if (out != null) out.close();

                // Assign file descriptor for MediaRecorder
                mediaRecorder.setOutputFile(Objects.requireNonNull(getContentResolver()
                        .openFileDescriptor(videoUri, "rw")).getFileDescriptor());
            } else {

                outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        fileName + EXTENSION_MP4);
                mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            }

            // Set encoding options
            mediaRecorder.setVideoSize(720, 1280);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            mediaRecorder.prepare();

            final Surface inputSurface = mediaRecorder.getSurface();

            // Start virtual display that mirrors the screen
            mediaProjection.createVirtualDisplay(
                    "ScreenRecorder",
                    720,
                    1280,
                    getResources().getDisplayMetrics().densityDpi,
                    0,
                    inputSurface,
                    null,
                    null
            );

            mediaRecorder.start();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            stopSelf();
        }
    }

    /**
     * Creates a foreground notification required for screen recording services.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private Notification createNotification() {

        final NotificationChannel channel = new NotificationChannel(
                ID_CHANNEL,
                NAME_CHANNEL,
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        return new Notification.Builder(this, ID_CHANNEL)
                .setContentTitle("Screen Recording")
                .setContentText("Recording in progress...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();
    }

    /**
     * Called when the service is stopped. Releases recorder and projection resources.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop and release MediaRecorder
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
            mediaRecorder.reset();
            mediaRecorder.release();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }

        // On older Android versions, trigger media scan so file appears in gallery
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && outputFile != null) {

            final Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(outputFile));
            sendBroadcast(mediaScanIntent);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private File outputFile;

    private static final String EXTENSION_MP4 = ".mp4";
    private static final String MIME_TYPE_MP4 = "video/mp4";

    private static final String NAME_RECORDING_FOLDER = "/ScreenRecords";

    private static final String ID_CHANNEL = "merabills_recording";
    private static final String NAME_CHANNEL = "MerabillsRecording";
}
