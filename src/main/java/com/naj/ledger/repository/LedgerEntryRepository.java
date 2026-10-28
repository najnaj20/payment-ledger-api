package com.naj.ledger.repository;

import com.naj.ledger.entity.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(UUID accountId);

    List<LedgerEntry> findByTransferId(UUID transferId);

    /**
     * The balance is always derived from the append-only ledger:
     * DEBIT entries add, CREDIT entries subtract. There is no mutable
     * balance column on the account, so a balance can never drift from
     * the entries that produced it.
     */
    @Query("""
            select coalesce(sum(case when e.type = 'DEBIT' then e.amountMinor
                                     else -e.amountMinor end), 0)
            from LedgerEntry e where e.accountId = :accountId
            """)
    long balanceForAccount(@Param("accountId") UUID accountId);
}
