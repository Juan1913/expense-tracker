package com.ExpenseTracker.app.tag.service;

import com.ExpenseTracker.app.tag.mapper.TagMapper;
import com.ExpenseTracker.app.tag.persistence.entity.TagEntity;
import com.ExpenseTracker.app.tag.persistence.repository.TagEntityRepository;
import com.ExpenseTracker.app.tag.presentation.dto.CreateTagDTO;
import com.ExpenseTracker.app.tag.presentation.dto.TagDTO;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TagServiceImpl implements ITagService {

    private final TagEntityRepository tagRepository;
    private final UserEntityRepository userRepository;
    private final TagMapper tagMapper;

    @Override
    public TagDTO create(CreateTagDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));
        TagEntity entity = tagMapper.toEntity(dto);
        entity.setUser(user);
        return tagMapper.toDTO(tagRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagDTO> findAllByUser(UUID userId) {
        return tagRepository.findByUser_Id(userId).stream()
                .map(tagMapper::toDTO)
                .toList();
    }

    @Override
    public void delete(UUID id, UUID userId) {
        TagEntity entity = tagRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Etiqueta no encontrada con id: " + id));
        tagRepository.delete(entity);
    }
}
