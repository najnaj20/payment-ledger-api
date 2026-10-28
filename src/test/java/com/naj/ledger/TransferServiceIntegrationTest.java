package com.naj.ledger;

import com.naj.ledger.dto.CreateAccountRequest;
import com.naj.ledger.dto.CreateTransferRequest;
import com.naj.ledger.dto.TransferResponse;
import com.naj.ledger.entity.EntryType;
import com.naj.ledger.entity.LedgerEntry;
import com.naj.ledger.exception.IdempotencyConflictException;
import com.naj.ledger.exception.InsufficientFundsException;
import com.naj.ledger.repository.LedgerEntryRepository;
import com.naj.ledger.repository.TransferRepository;
import com.naj.ledger.service.AccountService;
import com.naj.ledger.service.TransferService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TransferServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transfers;

    @Autowired
    private LedgerEntryRepository entries;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        // @ServiceConnection covers the datasource; this is a safety net
        // if the test runtime does not honour it.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void transferCreatesExactlyTwoBalancedEntries() {
        UUID sender = accountService.createAccount(new CreateAccountRequest("Alya", "IDR")).id();
        UUID receiver = accountService.createAccount(new CreateAccountRequest("Bima", "IDR")).id();
        seedFunding(sender, 500_000L);

        TransferResponse transfer = transferService.createTransfer(
                new CreateTransferRequest(sender, receiver, 100_000L, "IDR"), "key-balanced");

        List<LedgerEntry> posted = entries.findByTransferId(transfer.id());
        assertThat(posted).hasSize(2);
        long debits = posted.stream().filter(e -> e.getType() == EntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmountMinor).sum();
        long credits = posted.stream().filter(e -> e.getType() == EntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmountMinor).sum();
        assertThat(debits).isEqualTo(credits).isEqualTo(100_000L);
        assertThat(accountService.getAccount(sender).balanceMinor()).isEqualTo(400_000L);
        assertThat(accountService.getAccount(receiver).balanceMinor()).isEqualTo(100_000L);
    }

    @Test
    void idempotentRetryReturnsOriginalTransferWithoutDuplicating() {
        UUID sender = accountService.createAccount(new CreateAccountRequest("Alya", "IDR")).id();
        UUID receiver = accountService.createAccount(new CreateAccountRequest("Bima", "IDR")).id();
        seedFunding(sender, 200_000L);
        CreateTransferRequest request = new CreateTransferRequest(sender, receiver, 50_000L, "IDR");

        TransferResponse first = transferService.createTransfer(request, "key-retry");
        TransferResponse second = transferService.createTransfer(request, "key-retry");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(entries.findByTransferId(first.id())).hasSize(2);
        assertThat(accountService.getAccount(receiver).balanceMinor()).isEqualTo(50_000L);
        assertThat(accountService.getAccount(sender).balanceMinor()).isEqualTo(150_000L);
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflict() {
        UUID sender = accountService.createAccount(new CreateAccountRequest("Alya", "IDR")).id();
        UUID receiver = accountService.createAccount(new CreateAccountRequest("Bima", "IDR")).id();
        seedFunding(sender, 200_000L);

        transferService.createTransfer(
                new CreateTransferRequest(sender, receiver, 50_000L, "IDR"), "key-conflict");

        assertThatThrownBy(() -> transferService.createTransfer(
                new CreateTransferRequest(sender, receiver, 60_000L, "IDR"), "key-conflict"))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(accountService.getAccount(receiver).balanceMinor()).isEqualTo(50_000L);
        assertThat(accountService.getAccount(sender).balanceMinor()).isEqualTo(150_000L);
    }

    @Test
    void insufficientFundsRollsBackEverything() {
        UUID sender = accountService.createAccount(new CreateAccountRequest("Alya", "IDR")).id();
        UUID receiver = accountService.createAccount(new CreateAccountRequest("Bima", "IDR")).id();
        seedFunding(sender, 10_000L);

        assertThatThrownBy(() -> transferService.createTransfer(
                new CreateTransferRequest(sender, receiver, 99_999L, "IDR"), "key-poor"))
                .isInstanceOf(InsufficientFundsException.class);

        // The only entry on the sender is the funding entry; no partial
        // transfer entry remains after the rollback.
        assertThat(entries.findByAccountIdOrderByCreatedAtAsc(sender)).hasSize(1);
        assertThat(entries.findByAccountIdOrderByCreatedAtAsc(receiver)).isEmpty();
        assertThat(accountService.getAccount(sender).balanceMinor()).isEqualTo(10_000L);
    }

    @Test
    void concurrentTransfersNeverOverdraw() throws Exception {
        UUID sender = accountService.createAccount(new CreateAccountRequest("Alya", "IDR")).id();
        UUID receiver = accountService.createAccount(new CreateAccountRequest("Bima", "IDR")).id();
        seedFunding(sender, 10_000L);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int n = i;
            tasks.add(() -> {
                try {
                    transferService.createTransfer(
                            new CreateTransferRequest(sender, receiver, 1_000L, "IDR"),
                            "key-concurrent-" + n);
                    return true;
                } catch (InsufficientFundsException e) {
                    return false;
                }
            });
        }
        List<Future<Boolean>> results = pool.invokeAll(tasks);
        pool.shutdown();

        long succeeded = results.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return false;
            }
        }).count();

        assertThat(succeeded).isEqualTo(10);
        assertThat(accountService.getAccount(sender).balanceMinor()).isZero();
        assertThat(accountService.getAccount(receiver).balanceMinor()).isEqualTo(10_000L);
    }

    /**
     * Test-only funding: a balanced funding transfer from a throwaway
     * external account, standing in for money that arrived from outside
     * this service (for example, a bank deposit). It posts both entries
     * through the repositories so the foreign keys in the schema stay
     * satisfied; production transfers always go through TransferService.
     */
    private void seedFunding(UUID accountId, long amountMinor) {
        UUID external = accountService.createAccount(new CreateAccountRequest("External", "IDR")).id();
        com.naj.ledger.entity.Transfer funding = transfers.save(
                new com.naj.ledger.entity.Transfer(
                        external, accountId, amountMinor, "IDR", "funding-" + UUID.randomUUID()));
        entries.save(new LedgerEntry(funding.getId(), external,
                EntryType.CREDIT, amountMinor, "IDR"));
        entries.save(new LedgerEntry(funding.getId(), accountId,
                EntryType.DEBIT, amountMinor, "IDR"));
    }
}
