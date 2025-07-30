package com.merabills.videorecorder;

import static android.app.Activity.RESULT_OK;
import static com.merabills.videorecorder.ScreenRecorderBroadcastReceiver.KEY_ACTION;
import static com.merabills.videorecorder.ScreenRecorderBroadcastReceiver.VALUE_DESTROY;
import static com.merabills.videorecorder.ScreenRecorderBroadcastReceiver.VALUE_RESTART;
import static com.merabills.videorecorder.ScreenRecorderBroadcastReceiver.VALUE_STOP;

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

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenRecorderService extends Service {

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @RequiresPermission(allOf = {"android.permission.RECORD_AUDIO"})
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        final String action = intent.getStringExtra(KEY_ACTION);
        if (Objects.equals(action, VALUE_RESTART))
            initRecording();
        else if (Objects.equals(action, VALUE_STOP))
            stopRecording();
        else if (Objects.equals(action, VALUE_DESTROY))
            stopSelf();
        else {
            startForeground(1, createNotification());
            initRecording();
        }
        return START_NOT_STICKY;
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void initRecording() {

        final MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        final int resultCode = MainActivity.resultCode;
        final Intent data = MainActivity.data;

        if (resultCode == RESULT_OK && data != null) {

            mediaProjection = mpm.getMediaProjection(resultCode, data);
            if (mediaProjection != null)
                startRecording();
        } else
            Log.e(TAG, "MediaProjection initialization failed");
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startRecording() {
        try {

            final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            final String fileName = "recording_" + timestamp;

            outputFile = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES + NAME_RECORDING_FOLDER),
                    fileName + EXTENSION_MP4
            );

            final File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists())
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();

            final MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
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

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void stopRecording() {

        Log.d(TAG, "Stopping recording");

        if (screenMicRecorder != null) screenMicRecorder.stop();
        if (internalAudioRecorder != null) internalAudioRecorder.stop();
        if (muxerCoordinator != null) muxerCoordinator.stopMuxer();

        if (outputFile != null && outputFile.exists()) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(outputFile));
            sendBroadcast(mediaScanIntent);
            executor.submit(() -> AzureUploader.uploadVideoToAzure(outputFile, outputFile.getName()));
        }
    }

    private Notification createNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Screen Recorder", NotificationManager.IMPORTANCE_LOW
            );
            final NotificationManager manager = getSystemService(NotificationManager.class);
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

    private static final String CHANNEL_ID = "ScreenRecorderChannel";
    private static final String TAG = "ScreenRecorderService";
    private static final String EXTENSION_MP4 = ".mp4";
    private static final String NAME_RECORDING_FOLDER = "/ScreenRecords";
    private MediaProjection mediaProjection;
    private ScreenMicRecorder screenMicRecorder;
    private InternalAudioRecorder internalAudioRecorder;
    private MuxerCoordinator muxerCoordinator;
    private File outputFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
}
