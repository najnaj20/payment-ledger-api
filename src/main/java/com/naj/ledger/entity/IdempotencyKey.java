package com.naj.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The client-supplied Idempotency-Key header value. Unique in the
     * database, so two different transfers can never be recorded under
     * the same key, even under concurrency.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String key;

    /** SHA-256 of the canonical transfer payload, used to detect reuse. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String key, String requestHash, UUID transferId) {
        this.key = key;
        this.requestHash = requestHash;
        this.transferId = transferId;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
