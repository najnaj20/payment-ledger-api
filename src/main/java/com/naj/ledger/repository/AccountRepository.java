package com.naj.ledger.repository;

import com.naj.ledger.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Loads an account row with a pessimistic write lock, held until the
     * surrounding transaction commits or rolls back. Callers must lock
     * the two accounts of a transfer in a consistent order (sorted by id)
     * so concurrent transfers can never deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
