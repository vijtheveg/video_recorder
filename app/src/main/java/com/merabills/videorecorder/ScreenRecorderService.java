package com.merabills.videorecorder;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenRecorderService extends Service {

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @RequiresPermission(allOf = {"android.permission.RECORD_AUDIO"})
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            Log.e(TAG, "Intent is null, cannot start service");
            return START_NOT_STICKY;
        }

        final String action = intent.getStringExtra(Constants.KEY_ACTION);
        final String fileName = intent.getStringExtra(Constants.KEY_FILE_NAME);

        if (fileName == null || fileName.trim().isEmpty()) {
            Log.e(TAG, "File name is missing");
            return START_NOT_STICKY;
        }

        switch (Objects.requireNonNullElse(action, "")) {
            case Constants.VALUE_RESTART:
                initRecording(fileName);
                break;
            case Constants.VALUE_STOP:
                stopRecording();
                break;
            case Constants.VALUE_DESTROY:
                stopSelf();
                break;
            default:
                startForeground(1, createNotification());
                initRecording(fileName);
                break;
        }
        return START_NOT_STICKY;
    }

    /**
     * Initializes MediaProjection and starts screen/audio recording.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void initRecording(@NonNull String fileName) {

        final MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (mpm == null) {
            Log.e(TAG, "MediaProjectionManager is null");
            return;
        }

        final int resultCode = MainActivity.resultCode;
        final Intent data = MainActivity.data;

        if (resultCode == RESULT_OK && data != null) {

            mediaProjection = mpm.getMediaProjection(resultCode, data);
            if (mediaProjection != null)
                startRecording(fileName);
        } else {
            Log.e(TAG, "MediaProjection initialization failed");
        }
    }

    /**
     * Sets up muxer, file paths, and starts screen and audio recorders.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startRecording(@NonNull String fileName) {
        try {

            outputFile = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES + Constants.NAME_RECORDING_FOLDER),
                    fileName + Constants.EXTENSION_MP4);

            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean created = parent.mkdirs();
                if (!created) Log.w(TAG, "Failed to create parent directory");
            }
            final MediaMuxer muxer = new MediaMuxer(
                    outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            );
            muxerCoordinator = new MuxerCoordinator(muxer, 2); // 2 tracks: audio + video
            screenMicRecorder = new ScreenMicRecorder(mediaProjection, muxerCoordinator);
            internalAudioRecorder = new InternalAudioRecorder(mediaProjection, muxerCoordinator);
            screenMicRecorder.prepare();
            internalAudioRecorder.prepare();
            screenMicRecorder.start();
            internalAudioRecorder.start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            stopSelf();
        }
    }

    /**
     * Stops all recorders, closes the muxer, and uploads the file to Azure.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void stopRecording() {
        try {
            if (screenMicRecorder != null) screenMicRecorder.stop();
            if (internalAudioRecorder != null) internalAudioRecorder.stop();
            if (muxerCoordinator != null) muxerCoordinator.stopMuxer();
        } catch (Exception e) {
            Log.e(TAG, "Error while stopping recorders", e);
        }
        if (outputFile != null && outputFile.exists()) {

            // Update media store so the video is visible in gallery apps
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(outputFile));
            sendBroadcast(mediaScanIntent);

            // Upload to Azure asynchronously
            executor.submit(() -> AzureUploader.uploadVideoToAzure(outputFile, outputFile.getName()));
        } else {
            Log.w(TAG, "No output file to broadcast or upload");
        }
    }

    /**
     * Creates a low-importance persistent notification required for foreground service.
     */
    private Notification createNotification() {

        NotificationChannel channel = new NotificationChannel(
                Constants.CHANNEL_ID, Constants.CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
        );
        final NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);

        return new NotificationCompat.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle(getString(R.string.screen_recording_in_progress))
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a started service, not bound
    }

    private MediaProjection mediaProjection;
    private ScreenMicRecorder screenMicRecorder;
    private InternalAudioRecorder internalAudioRecorder;
    private MuxerCoordinator muxerCoordinator;
    private File outputFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String TAG = "ScreenRecorderService";
}
