package com.ExpenseTracker.app.account.presentation.dto;

import lombok.*;

/**
 * Preview of what will be affected when an account is deleted.
 * Used by the frontend to show a confirmation dialog before the actual
 * delete / hard-delete so the user knows what cascades.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountImpactDTO {
    private long transactions;
}
