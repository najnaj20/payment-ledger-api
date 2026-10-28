package com.naj.ledger.service;

import com.naj.ledger.dto.AccountResponse;
import com.naj.ledger.dto.CreateAccountRequest;
import com.naj.ledger.dto.LedgerEntryResponse;
import com.naj.ledger.entity.Account;
import com.naj.ledger.entity.EntryType;
import com.naj.ledger.entity.LedgerEntry;
import com.naj.ledger.entity.Transfer;
import com.naj.ledger.exception.NotFoundException;
import com.naj.ledger.repository.AccountRepository;
import com.naj.ledger.repository.LedgerEntryRepository;
import com.naj.ledger.repository.TransferRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final TransferRepository transfers;

    public AccountService(AccountRepository accounts, LedgerEntryRepository entries,
                          TransferRepository transfers) {
        this.accounts = accounts;
        this.entries = entries;
        this.transfers = transfers;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account account = accounts.save(new Account(request.owner(), request.currency()));
        if (request.initialBalanceMinor() <= 0) {
            return toResponse(account, 0L);
        }
        // Opening balance: money arriving from outside this service.
        // It is still posted as a balanced pair, CREDIT on a throwaway
        // external account and DEBIT on the new account, so the ledger
        // never contains a one-sided entry. Without this, a new account
        // would always start at zero and could never send a transfer.
        Account external = accounts.save(new Account("External", request.currency()));
        Transfer funding = transfers.save(new Transfer(
                external.getId(), account.getId(), request.initialBalanceMinor(),
                request.currency(), "opening-" + UUID.randomUUID()));
        entries.save(new LedgerEntry(funding.getId(), external.getId(),
                EntryType.CREDIT, request.initialBalanceMinor(), request.currency()));
        entries.save(new LedgerEntry(funding.getId(), account.getId(),
                EntryType.DEBIT, request.initialBalanceMinor(), request.currency()));
        return toResponse(account, request.initialBalanceMinor());
    }

    public AccountResponse getAccount(UUID id) {
        Account account = findAccount(id);
        return toResponse(account, entries.balanceForAccount(id));
    }

    public List<LedgerEntryResponse> getEntries(UUID accountId) {
        findAccount(accountId);
        return entries.findByAccountIdOrderByCreatedAtAsc(accountId).stream()
                .map(e -> new LedgerEntryResponse(
                        e.getId(), e.getTransferId(), e.getAccountId(),
                        e.getType().name(), e.getAmountMinor(), e.getCurrency(), e.getCreatedAt()))
                .toList();
    }

    private Account findAccount(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
    }

    private AccountResponse toResponse(Account account, long balanceMinor) {
        return new AccountResponse(
                account.getId(), account.getOwner(), account.getCurrency(),
                account.getStatus().name(), balanceMinor, account.getCreatedAt());
    }
}
