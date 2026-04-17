package com.ExpenseTracker.app.admin.presentation.controller;

import com.ExpenseTracker.app.admin.presentation.dto.AdminCreateUserDTO;
import com.ExpenseTracker.app.admin.service.IAdminUserService;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Users", description = "Gestión de usuarios (solo ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final IAdminUserService adminUserService;

    @PostMapping
    @Operation(summary = "Invitar usuario por correo")
    public ResponseEntity<UserDTO> inviteUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.inviteUser(dto));
    }

    @GetMapping
    @Operation(summary = "Listar todos los usuarios (paginado)")
    public ResponseEntity<Page<UserDTO>> listUsers(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminUserService.listUsers(pageable));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Activar / desactivar usuario")
    public ResponseEntity<UserDTO> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
