package com.merabills.videorecorder;

import android.Manifest;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class InternalAudioRecorder {
    public InternalAudioRecorder(
            @NonNull final MediaProjection projection,
            @NonNull final MuxerCoordinator muxerCoordinator
    ) {

        this.mediaProjection = projection;
        this.muxerCoordinator = muxerCoordinator;
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void prepare() throws IOException {

        final AudioPlaybackCaptureConfiguration config
                = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();

        final int sampleRate = 44100;
        final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build();

        final MediaFormat format =
                MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        sampleRate,
                        2);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
    }

    public void start() {

        isRecording = true;
        audioRecord.startRecording();
        recordingThread = new Thread(() -> {
            final ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
            final ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();
            final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            byte[] tempBuffer = new byte[4096];

            while (isRecording) {

                final int inputBufferIndex = audioEncoder.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {

                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    final int readBytes = audioRecord.read(tempBuffer, 0, tempBuffer.length);
                    if (readBytes > 0) {

                        inputBuffer.put(tempBuffer, 0, readBytes);
                        final long presentationTimeUs = System.nanoTime() / 1000;
                        audioEncoder.queueInputBuffer(inputBufferIndex, 0, readBytes, presentationTimeUs, 0);
                    }
                }

                int outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 10000);
                while (outputBufferIndex >= 0) {

                    final ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
                        bufferInfo.size = 0;

                    if (bufferInfo.size > 0) {

                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        if (audioTrackIndex == -1) {

                            final MediaFormat trackFormat = audioEncoder.getOutputFormat();
                            audioTrackIndex = muxerCoordinator.addTrack(trackFormat);
                        }

                        while (!muxerCoordinator.isStarted()) {
                            try {
                                Thread.sleep(5);
                            } catch (Exception e) {
                               Log.e("InternalAudioRecorder", Objects.requireNonNull(e.getMessage()));
                            }
                        }

                        muxerCoordinator.getMuxer().writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
                    }
                    audioEncoder.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
                }
            }
        });
        recordingThread.start();
    }

    public void stop() {

        isRecording = false;
        if (recordingThread != null) {

            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Recording thread join failed", e);
            }
        }

        if (audioRecord != null) {

            audioRecord.stop();
            audioRecord.release();
        }
        if (audioEncoder != null) {

            audioEncoder.stop();
            audioEncoder.release();
        }
    }

    private static final String TAG = "InternalAudioRecorder";
    private final MediaProjection mediaProjection;
    private final MuxerCoordinator muxerCoordinator;

    private AudioRecord audioRecord;
    private MediaCodec audioEncoder;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
    private int audioTrackIndex = -1;
}