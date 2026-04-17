package com.ExpenseTracker.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WasabiStorageServiceTest {

    @Mock private S3Client s3Client;
    @Mock private MultipartFile file;

    private WasabiStorageService service;

    private static final String ENDPOINT = "https://s3.us-east-1.wasabisys.com";
    private static final String BUCKET = "finz-bucket";

    @BeforeEach
    void setUp() {
        service = new WasabiStorageService(s3Client);
        ReflectionTestUtils.setField(service, "bucket", BUCKET);
        ReflectionTestUtils.setField(service, "endpoint", ENDPOINT);
    }

    // ── upload ──────────────────────────────────────────────────────────────────

    @Test
    void upload_returnsUrlWithCorrectStructure() throws Exception {
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(2048L);
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = service.upload(file, "avatars");

        assertThat(url).startsWith(ENDPOINT + "/" + BUCKET + "/avatars/");
        assertThat(url).endsWith(".jpg");
    }

    @Test
    void upload_callsPutObjectWithCorrectBucketAndContentType() throws Exception {
        when(file.getOriginalFilename()).thenReturn("doc.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(512L);
        when(file.getBytes()).thenReturn(new byte[0]);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        service.upload(file, "documents");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.contentType()).isEqualTo("application/pdf");
        assertThat(request.key()).startsWith("documents/");
        assertThat(request.key()).endsWith(".pdf");
    }

    @Test
    void upload_whenFileHasNoExtension_usesBinFallback() throws Exception {
        when(file.getOriginalFilename()).thenReturn("noextension");
        when(file.getContentType()).thenReturn("application/octet-stream");
        when(file.getSize()).thenReturn(100L);
        when(file.getBytes()).thenReturn(new byte[0]);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = service.upload(file, "misc");

        assertThat(url).endsWith(".bin");
    }

    @Test
    void upload_whenS3Throws_propagatesRuntimeException() throws Exception {
        when(file.getOriginalFilename()).thenReturn("x.jpg");
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1L);
        when(file.getBytes()).thenReturn(new byte[0]);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> service.upload(file, "avatars"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al subir el archivo");
    }

    // ── delete ──────────────────────────────────────────────────────────────────

    @Test
    void delete_extractsKeyCorrectlyAndCallsDeleteObject() {
        String url = ENDPOINT + "/" + BUCKET + "/avatars/some-uuid.jpg";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        service.delete(url);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        DeleteObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo("avatars/some-uuid.jpg");
    }

    @Test
    void delete_whenS3Throws_doesNotPropagateException() {
        String url = ENDPOINT + "/" + BUCKET + "/avatars/file.jpg";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatCode(() -> service.delete(url)).doesNotThrowAnyException();
    }
}
