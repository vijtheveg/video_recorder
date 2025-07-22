package com.merabills.videorecorder;

import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class ScreenMicRecorder {
    private static final String TAG = "ScreenMicRecorder";

    private final MediaProjection mediaProjection;
    private final MuxerCoordinator muxerCoordinator;
    private MediaCodec videoEncoder;
    private VirtualDisplay virtualDisplay;
    private Thread encodingThread;

    private boolean isRecording = false;
    private int videoTrackIndex = -1;

    public ScreenMicRecorder(MediaProjection mediaProjection, MuxerCoordinator muxerCoordinator) {
        this.mediaProjection = mediaProjection;
        this.muxerCoordinator = muxerCoordinator;
    }

    public void prepare() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", 720, 1280);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 5 * 1024 * 1024);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        videoEncoder = MediaCodec.createEncoderByType("video/avc");
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface inputSurface = videoEncoder.createInputSurface();
        videoEncoder.start();

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenRecorder",
                720, 1280, 1,
                0,
                inputSurface,
                null, null
        );
    }

    public void start() {
        isRecording = true;
        encodingThread = new Thread(() -> {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while (isRecording) {
                int outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 10000);

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = videoEncoder.getOutputFormat();
                    videoTrackIndex = muxerCoordinator.addTrack(newFormat);
                } else if (outputBufferIndex >= 0) {
                    ByteBuffer encodedData = videoEncoder.getOutputBuffer(outputBufferIndex);
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex + " was null");
                    }

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }

                    if (bufferInfo.size != 0) {
                        while (!muxerCoordinator.isStarted()) {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Waiting for muxer to start", e);
                            }
                        }

                        encodedData.position(bufferInfo.offset);
                        encodedData.limit(bufferInfo.offset + bufferInfo.size);
                        muxerCoordinator.getMuxer().writeSampleData(videoTrackIndex, encodedData, bufferInfo);
                    }

                    videoEncoder.releaseOutputBuffer(outputBufferIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
        });
        encodingThread.start();
    }

    public void stop() {
        isRecording = false;
        if (encodingThread != null) {
            try {
                encodingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Encoding thread join interrupted", e);
            }
        }

        if (virtualDisplay != null) virtualDisplay.release();
        if (videoEncoder != null) {
            videoEncoder.signalEndOfInputStream();
            videoEncoder.stop();
            videoEncoder.release();
        }
    }

}
