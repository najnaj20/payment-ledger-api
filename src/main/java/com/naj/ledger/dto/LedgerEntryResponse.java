package com.naj.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transferId,
        UUID accountId,
        String type,
        long amountMinor,
        String currency,
        Instant createdAt
) {
}
