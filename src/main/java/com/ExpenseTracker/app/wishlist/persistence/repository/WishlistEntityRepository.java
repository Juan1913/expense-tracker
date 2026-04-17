package com.ExpenseTracker.app.wishlist.persistence.repository;

import com.ExpenseTracker.app.wishlist.persistence.entity.WishlistEntity;
import com.ExpenseTracker.util.enums.WishlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistEntityRepository extends JpaRepository<WishlistEntity, UUID> {

    List<WishlistEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    List<WishlistEntity> findByUser_IdAndStatusOrderByCreatedAtDesc(UUID userId, WishlistStatus status);

    Optional<WishlistEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
