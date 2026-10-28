package com.naj.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinor,
        String currency,
        String status,
        String idempotencyKey,
        Instant createdAt
) {
}
