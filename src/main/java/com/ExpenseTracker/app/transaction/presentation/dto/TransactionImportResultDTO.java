package com.ExpenseTracker.app.transaction.presentation.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionImportResultDTO {

    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int createdRows;
    private boolean dryRun;
    private List<TransactionImportRowDTO> rows;
}
