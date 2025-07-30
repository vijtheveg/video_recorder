package com.merabills.videorecorder;

public class Constants {
    static final String AZURE_ENDPOINT = "https://testrajat.blob.core.windows.net";
    static final String AZURE_CONTAINER_NAME = "videos";
    static final String AZURE_SAS_CREDENTIALS = "sv=2024-11-04&ss=bfqt&srt=co&sp=rwdlacupiytfx&se=2025-08-30T17:20:42Z&st=2025-07-30T09:05:42Z&spr=https&sig=7dp0KWmKTSB%2BIJUr6RU0yC78PuhjMRGW6TQjyJ%2BD%2Bb8%3D";
    public static final String KEY_ACTION = "action";
    public static final String KEY_FILE_NAME = "file_name";
    public static final String VALUE_RESTART = "restart";
    public static final String VALUE_STOP = "stop";
    public static final String VALUE_DESTROY = "destroy";

    public static final String CHANNEL_ID = "ScreenRecorderChannel";
    public static final String EXTENSION_MP4 = ".mp4";
    public static final String NAME_RECORDING_FOLDER = "/ScreenRecords";
    public static CharSequence CHANNEL_NAME = "Screen Recorder";
}
