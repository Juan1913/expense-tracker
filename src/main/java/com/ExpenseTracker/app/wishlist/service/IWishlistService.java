package com.ExpenseTracker.app.wishlist.service;

import com.ExpenseTracker.app.wishlist.presentation.dto.CreateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.UpdateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.WishlistDTO;
import com.ExpenseTracker.util.enums.WishlistStatus;

import java.util.List;
import java.util.UUID;

public interface IWishlistService {

    WishlistDTO create(CreateWishlistDTO dto, UUID userId);

    List<WishlistDTO> findAllByUser(UUID userId, WishlistStatus status);

    WishlistDTO findById(UUID id, UUID userId);

    WishlistDTO update(UUID id, UpdateWishlistDTO dto, UUID userId);

    void delete(UUID id, UUID userId);
}
