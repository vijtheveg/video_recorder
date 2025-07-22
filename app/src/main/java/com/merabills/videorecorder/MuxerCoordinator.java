package com.merabills.videorecorder;

import android.media.MediaFormat;
import android.media.MediaMuxer;

public class MuxerCoordinator {
    private final MediaMuxer muxer;
    private final int expectedTracks;
    private int addedTracks = 0;
    private boolean started = false;

    public MuxerCoordinator(MediaMuxer muxer, int expectedTracks) {
        this.muxer = muxer;
        this.expectedTracks = expectedTracks;
    }

    public synchronized int addTrack(MediaFormat format) {
        int trackIndex = muxer.addTrack(format);
        addedTracks++;
        if (addedTracks == expectedTracks && !started) {
            muxer.start();
            started = true;
        }
        return trackIndex;
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public MediaMuxer getMuxer() {
        return muxer;
    }

    public synchronized void stopMuxer() {
        if (started) {
            try {
                muxer.stop();
                muxer.release();
                started = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
