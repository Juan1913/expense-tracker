package com.ExpenseTracker.app.transaction.presentation.dto;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Una transacción extraída de un extracto bancario.")
public record ExtractedTransactionDTO(
        @JsonPropertyDescription("Fecha en formato yyyy-MM-dd. Si el extracto no tiene año, usar el año actual.")
        String date,

        @JsonPropertyDescription("Tipo de movimiento. Valores permitidos: INCOME (abono/crédito/depósito) o EXPENSE (cargo/débito/compra).")
        String type,

        @JsonPropertyDescription("Monto absoluto en pesos colombianos, sólo número sin símbolos ni separador de miles. Usá punto como separador decimal.")
        String amount,

        @JsonPropertyDescription("Descripción concisa del movimiento (comercio, motivo). Limpiá referencias bancarias internas.")
        String description,

        @JsonPropertyDescription("Categoría sugerida (ej. Restaurantes, Mercado, Transporte, Servicios, Salud, Salario, Trabajo extra). Devolvé un nombre corto en español.")
        String suggestedCategory
) {}
