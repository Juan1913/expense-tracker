package com.ExpenseTracker.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasabiStorageService implements StorageService {

    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.storage.bucket}")
    private String bucket;

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

            return key;
        } catch (Exception e) {
            log.error("Error subiendo archivo a Wasabi: {}", e.getMessage());
            throw new RuntimeException("Error al subir el archivo", e);
        }
    }

    @Override
    public String presignedUrl(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) return null;

        // Compatibilidad con datos viejos: si está guardado un URL completo, lo devolvemos tal cual.
        if (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://")) {
            return keyOrUrl;
        }

        try {
            GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                    .signatureDuration(PRESIGN_TTL)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(keyOrUrl).build())
                    .build();
            return s3Presigner.presignGetObject(req).url().toString();
        } catch (Exception e) {
            log.error("Error generando URL prefirmado para key {}: {}", keyOrUrl, e.getMessage());
            return null;
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            String resolvedKey = key.startsWith("http") ? extractKeyFromUrl(key) : key;
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(resolvedKey).build());
        } catch (Exception e) {
            log.warn("No se pudo eliminar archivo de storage: {}", e.getMessage());
        }
    }

    private String extractKeyFromUrl(String url) {
        int idx = url.indexOf(bucket + "/");
        return idx >= 0 ? url.substring(idx + bucket.length() + 1) : url;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "bin";
    }
}
