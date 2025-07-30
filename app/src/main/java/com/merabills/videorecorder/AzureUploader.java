package com.merabills.videorecorder;

import androidx.annotation.NonNull;

import com.azure.core.credential.AzureSasCredential;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to upload video files to Azure Blob Storage using SAS authentication.
 */
public class AzureUploader {

    /**
     * Uploads the given video file to Azure Blob Storage with the specified blob name.
     *
     * @param videoFile The local video file to upload.
     * @param blobName  The name to assign to the blob in Azure Storage.
     */
    public static void uploadVideoToAzure(
            @NonNull File videoFile,
            @NonNull String blobName
    ) {

        // Validate input: check if the file exists and is not null
        if (!videoFile.exists()) {

            System.err.println("Upload failed: File does not exist.");
            return;
        }

        // Use try-with-resources to ensure the input stream is closed automatically
        try (InputStream stream = new FileInputStream(videoFile)) {

            // Build the BlobClient with endpoint, container, blob name, and SAS credentials
            BlobClient blobClient = new BlobClientBuilder()
                    .endpoint(Constants.AZURE_ENDPOINT)
                    .containerName(Constants.AZURE_CONTAINER_NAME)
                    .blobName(blobName)
                    .credential(new AzureSasCredential(Constants.AZURE_SAS_CREDENTIALS))
                    .buildClient();

            // Upload the file stream to Azure (overwrite if blob already exists)
            blobClient.upload(stream, videoFile.length(), true);

            // Log success message
            System.out.println("Upload successful: " + blobName);

        } catch (IOException io) {
            // Handle file I/O errors (e.g., read permission issues)
            System.err.println("Upload failed: Unable to read the file. " + io.getMessage());
        } catch (Exception e) {
            // Catch all other exceptions during upload (e.g., auth/network issues)
            System.err.println("Upload failed: " + e.getMessage());
        }
    }
}
