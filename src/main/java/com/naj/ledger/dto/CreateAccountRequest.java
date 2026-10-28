package com.naj.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateAccountRequest(
        @NotBlank String owner,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code") String currency,
        @PositiveOrZero long initialBalanceMinor
) {
    public CreateAccountRequest(String owner, String currency) {
        this(owner, currency, 0L);
    }
}
