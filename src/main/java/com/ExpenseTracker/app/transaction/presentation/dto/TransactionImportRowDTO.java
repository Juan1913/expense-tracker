package com.ExpenseTracker.app.transaction.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionImportRowDTO {

    private int row;

    private String date;
    private String type;
    private String amount;
    private String accountName;
    private String transferToAccountName;
    private String categoryName;
    private String description;

    private boolean valid;
    private String errorMessage;
    private boolean created;
}
