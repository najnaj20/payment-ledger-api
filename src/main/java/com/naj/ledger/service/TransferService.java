package com.naj.ledger.service;

import com.naj.ledger.dto.CreateTransferRequest;
import com.naj.ledger.dto.TransferResponse;
import com.naj.ledger.entity.Account;
import com.naj.ledger.entity.AccountStatus;
import com.naj.ledger.entity.EntryType;
import com.naj.ledger.entity.IdempotencyKey;
import com.naj.ledger.entity.LedgerEntry;
import com.naj.ledger.entity.Transfer;
import com.naj.ledger.exception.BadRequestException;
import com.naj.ledger.exception.IdempotencyConflictException;
import com.naj.ledger.exception.InsufficientFundsException;
import com.naj.ledger.exception.NotFoundException;
import com.naj.ledger.repository.AccountRepository;
import com.naj.ledger.repository.IdempotencyKeyRepository;
import com.naj.ledger.repository.LedgerEntryRepository;
import com.naj.ledger.repository.TransferRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;
    private final IdempotencyKeyRepository idempotencyKeys;

    public TransferService(AccountRepository accounts, TransferRepository transfers,
                           LedgerEntryRepository entries, IdempotencyKeyRepository idempotencyKeys) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.entries = entries;
        this.idempotencyKeys = idempotencyKeys;
    }

    /**
     * Creates a transfer as one atomic unit: the transfer row, exactly two
     * ledger entries, and the idempotency record are written in a single
     * database transaction. Any failure rolls all of them back, so a
     * transfer can never leave a single, unbalanced entry behind.
     */
    @Transactional
    public TransferResponse createTransfer(CreateTransferRequest request, String idempotencyKeyValue) {
        if (idempotencyKeyValue == null || idempotencyKeyValue.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        if (request.amountMinor() <= 0) {
            throw new BadRequestException("amountMinor must be greater than 0");
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BadRequestException("fromAccountId and toAccountId must differ");
        }

        String requestHash = sha256Hex(canonicalPayload(request));

        // Idempotency, checked first so a replay never re-runs the transfer.
        // Same key + same payload -> return the original transfer.
        // Same key + different payload -> 409, the key must not be reused.
        var existing = idempotencyKeys.findByKey(idempotencyKeyValue);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key was already used with a different payload");
            }
            Transfer original = transfers.findById(existing.get().getTransferId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record points to a missing transfer"));
            return toResponse(original);
        }

        // Lock both account rows in a consistent order (sorted by id) so
        // two concurrent transfers between the same accounts can never
        // deadlock by locking in opposite orders. The locks are held until
        // this transaction commits, which also serialises the balance
        // check below against other transfers touching these accounts.
        List<UUID> lockedIds = List.of(request.fromAccountId(), request.toAccountId())
                .stream().sorted().toList();
        Map<UUID, Account> locked = lockedIds.stream()
                .map(id -> accounts.findByIdForUpdate(id)
                        .orElseThrow(() -> new NotFoundException("Account not found: " + id)))
                .collect(Collectors.toMap(Account::getId, a -> a));

        Account sender = locked.get(request.fromAccountId());
        Account receiver = locked.get(request.toAccountId());
        if (sender.getStatus() != AccountStatus.ACTIVE || receiver.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Both accounts must be ACTIVE");
        }
        if (!sender.getCurrency().equals(request.currency())
                || !receiver.getCurrency().equals(request.currency())) {
            throw new BadRequestException("Transfer currency must match both accounts");
        }

        long senderBalance = entries.balanceForAccount(sender.getId());
        if (senderBalance < request.amountMinor()) {
            throw new InsufficientFundsException("Sender has insufficient funds");
        }

        Transfer transfer = transfers.save(new Transfer(
                request.fromAccountId(), request.toAccountId(),
                request.amountMinor(), request.currency(), idempotencyKeyValue));

        List<LedgerEntry> posted = new ArrayList<>();
        // Double entry: CREDIT decreases the sender (asset) account,
        // DEBIT increases the receiver (asset) account, same amount.
        posted.add(entries.save(new LedgerEntry(
                transfer.getId(), sender.getId(), EntryType.CREDIT,
                request.amountMinor(), request.currency())));
        posted.add(entries.save(new LedgerEntry(
                transfer.getId(), receiver.getId(), EntryType.DEBIT,
                request.amountMinor(), request.currency())));

        // Defensive invariant check: debit total must equal credit total.
        long debits = posted.stream()
                .filter(e -> e.getType() == EntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmountMinor).sum();
        long credits = posted.stream()
                .filter(e -> e.getType() == EntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmountMinor).sum();
        if (debits != credits) {
            throw new IllegalStateException("Double-entry invariant violated: debits != credits");
        }

        // saveAndFlush surfaces a unique-constraint violation here, inside
        // the transaction, if a concurrent request won the same key first.
        // That case is mapped to 409; a client retry then returns the
        // original transfer through the check above.
        idempotencyKeys.saveAndFlush(
                new IdempotencyKey(idempotencyKeyValue, requestHash, transfer.getId()));

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID id) {
        Transfer transfer = transfers.findById(id)
                .orElseThrow(() -> new NotFoundException("Transfer not found: " + id));
        return toResponse(transfer);
    }

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(), transfer.getFromAccountId(), transfer.getToAccountId(),
                transfer.getAmountMinor(), transfer.getCurrency(), transfer.getStatus().name(),
                transfer.getIdempotencyKey(), transfer.getCreatedAt());
    }

    private String canonicalPayload(CreateTransferRequest request) {
        return request.fromAccountId() + "|" + request.toAccountId()
                + "|" + request.amountMinor() + "|" + request.currency();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
