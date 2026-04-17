package com.ExpenseTracker.app.wishlist.service;

import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.app.wishlist.mapper.WishlistMapper;
import com.ExpenseTracker.app.wishlist.persistence.entity.WishlistEntity;
import com.ExpenseTracker.app.wishlist.persistence.repository.WishlistEntityRepository;
import com.ExpenseTracker.app.wishlist.presentation.dto.CreateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.UpdateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.WishlistDTO;
import com.ExpenseTracker.util.enums.WishlistStatus;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WishlistServiceImpl implements IWishlistService {

    private final WishlistEntityRepository wishlistRepository;
    private final UserEntityRepository userRepository;
    private final WishlistMapper wishlistMapper;

    @Override
    public WishlistDTO create(CreateWishlistDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));

        WishlistEntity entity = wishlistMapper.toEntity(dto);
        entity.setUser(user);

        return wishlistMapper.toDTO(wishlistRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WishlistDTO> findAllByUser(UUID userId, WishlistStatus status) {
        if (status != null) {
            return wishlistRepository
                    .findByUser_IdAndStatusOrderByCreatedAtDesc(userId, status)
                    .stream().map(wishlistMapper::toDTO).toList();
        }
        return wishlistRepository
                .findByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(wishlistMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistDTO findById(UUID id, UUID userId) {
        return wishlistRepository.findByIdAndUser_Id(id, userId)
                .map(wishlistMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Deseo no encontrado con id: " + id));
    }

    @Override
    public WishlistDTO update(UUID id, UpdateWishlistDTO dto, UUID userId) {
        WishlistEntity entity = wishlistRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Deseo no encontrado con id: " + id));

        wishlistMapper.updateEntityFromDTO(dto, entity);

        return wishlistMapper.toDTO(wishlistRepository.save(entity));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        WishlistEntity entity = wishlistRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Deseo no encontrado con id: " + id));
        wishlistRepository.delete(entity);
    }
}
