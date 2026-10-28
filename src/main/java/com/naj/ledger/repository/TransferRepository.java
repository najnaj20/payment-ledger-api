package com.naj.ledger.repository;

import com.naj.ledger.entity.Transfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
}
