package com.ExpenseTracker.infrastructure.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Carga centralizada de archivos a Wasabi S3")
@SecurityRequirement(name = "bearerAuth")
public class StorageController {

    private final StorageService storageService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Subir archivo",
            description = "Sube un archivo a Wasabi S3. Devuelve la key (para guardar) y un URL prefirmado (para preview).")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "uploads") String folder) {
        String key = storageService.upload(file, folder);
        String url = storageService.presignedUrl(key);
        return ResponseEntity.ok(Map.of("key", key, "url", url));
    }
}
