package com.ExpenseTracker.app.document.service;

import com.ExpenseTracker.app.document.presentation.dto.UserDocumentDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IUserDocumentService {

    UserDocumentDTO upload(MultipartFile file, UUID userId);

    List<UserDocumentDTO> findAllByUser(UUID userId);

    UserDocumentDTO findById(UUID id, UUID userId);

    void delete(UUID id, UUID userId);
}
