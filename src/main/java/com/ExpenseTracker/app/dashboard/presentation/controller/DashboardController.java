package com.ExpenseTracker.app.dashboard.presentation.controller;

import com.ExpenseTracker.app.dashboard.presentation.dto.DashboardSummaryDTO;
import com.ExpenseTracker.app.dashboard.service.IDashboardService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Resumen financiero del usuario")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final IDashboardService dashboardService;
    private final SecurityUtils securityUtils;

    @GetMapping("/summary")
    @Operation(summary = "Obtener resumen financiero del usuario autenticado")
    public ResponseEntity<DashboardSummaryDTO> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary(securityUtils.getCurrentUserId()));
    }
}
