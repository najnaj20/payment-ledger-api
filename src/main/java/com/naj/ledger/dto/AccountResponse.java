package com.naj.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String owner,
        String currency,
        String status,
        /** Balance in minor units, computed from ledger entries. */
        long balanceMinor,
        Instant createdAt
) {
}
