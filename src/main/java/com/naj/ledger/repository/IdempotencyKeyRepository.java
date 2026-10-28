package com.naj.ledger.repository;

import com.naj.ledger.entity.IdempotencyKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, java.util.UUID> {

    Optional<IdempotencyKey> findByKey(String key);
}
