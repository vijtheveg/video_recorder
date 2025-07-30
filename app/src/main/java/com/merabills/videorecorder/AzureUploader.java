package com.merabills.videorecorder;

import com.azure.core.credential.AzureSasCredential;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class AzureUploader {

    public static void uploadVideoToAzure(File videoFile, String blobName) {

        final String endPoint = "https://testrajat.blob.core.windows.net";
        final String containerName = "videos";
        final String credentials = "sv=2024-11-04&ss=bfqt&srt=co&sp=rwdlacupiytfx&se=2025-08-30T17:20:42Z&st=2025-07-30T09:05:42Z&spr=https&sig=7dp0KWmKTSB%2BIJUr6RU0yC78PuhjMRGW6TQjyJ%2BD%2Bb8%3D";
        try {

            BlobClient blobClient = new BlobClientBuilder()
                    .endpoint(endPoint)
                    .containerName(containerName)
                    .blobName(blobName)
                    .credential(new AzureSasCredential(credentials))
                    .buildClient();

            InputStream stream = new FileInputStream(videoFile);
            blobClient.upload(stream, videoFile.length(), true);
            System.out.println("Upload successful");
        } catch (Exception e) {
            System.err.println("Upload failed");
        }
    }
}
