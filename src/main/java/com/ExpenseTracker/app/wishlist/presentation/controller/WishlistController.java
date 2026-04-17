package com.ExpenseTracker.app.wishlist.presentation.controller;

import com.ExpenseTracker.app.wishlist.presentation.dto.CreateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.UpdateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.WishlistDTO;
import com.ExpenseTracker.app.wishlist.service.IWishlistService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import com.ExpenseTracker.util.enums.WishlistStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "Gestión de lista de deseos y metas de ahorro")
@SecurityRequirement(name = "bearerAuth")
public class WishlistController {

    private final IWishlistService wishlistService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Crear nuevo deseo o meta de ahorro")
    public ResponseEntity<WishlistDTO> create(@Valid @RequestBody CreateWishlistDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wishlistService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar deseos del usuario — filtro opcional ?status=ACTIVE|COMPLETED|CANCELLED")
    public ResponseEntity<List<WishlistDTO>> findAll(
            @RequestParam(required = false) WishlistStatus status) {
        return ResponseEntity.ok(wishlistService.findAllByUser(securityUtils.getCurrentUserId(), status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener deseo por ID")
    public ResponseEntity<WishlistDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(wishlistService.findById(id, securityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar deseo o registrar progreso de ahorro")
    public ResponseEntity<WishlistDTO> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateWishlistDTO dto) {
        return ResponseEntity.ok(wishlistService.update(id, dto, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar deseo")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        wishlistService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
