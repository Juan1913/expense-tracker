package com.ExpenseTracker.app.dashboard.service;

import com.ExpenseTracker.app.dashboard.presentation.dto.DashboardSummaryDTO;

import java.util.UUID;

public interface IDashboardService {

    DashboardSummaryDTO getSummary(UUID userId);
}
