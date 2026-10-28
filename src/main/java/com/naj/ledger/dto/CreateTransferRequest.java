package com.naj.ledger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @Positive long amountMinor,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code") String currency
) {
}
