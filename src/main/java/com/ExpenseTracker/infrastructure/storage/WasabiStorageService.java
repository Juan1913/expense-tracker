package com.ExpenseTracker.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasabiStorageService implements StorageService {

    private final S3Client s3Client;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.endpoint}")
    private String endpoint;

    @Override
    public String upload(MultipartFile file, String folder) {
        try {
            String ext = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
            String key = folder + "/" + UUID.randomUUID() + "." + ext;

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );

            return endpoint + "/" + bucket + "/" + key;
        } catch (Exception e) {
            log.error("Error subiendo archivo a Wasabi: {}", e.getMessage());
            throw new RuntimeException("Error al subir el archivo", e);
        }
    }

    @Override
    public void delete(String url) {
        try {
            String key = url.substring(url.indexOf(bucket + "/") + bucket.length() + 1);
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("No se pudo eliminar archivo de storage: {}", e.getMessage());
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "bin";
    }
}
