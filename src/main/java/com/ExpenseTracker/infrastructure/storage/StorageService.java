package com.ExpenseTracker.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /** Sube el archivo y devuelve la key (e.g. "avatars/uuid.jpg"). */
    String upload(MultipartFile file, String folder);

    /** URL prefirmado de lectura, válido temporalmente. Acepta key o URL legacy. */
    String presignedUrl(String keyOrUrl);

    void delete(String key);
}
