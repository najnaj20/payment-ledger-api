package com.naj.ledger.controller;

import com.naj.ledger.dto.AccountResponse;
import com.naj.ledger.dto.CreateAccountRequest;
import com.naj.ledger.dto.LedgerEntryResponse;
import com.naj.ledger.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "Create accounts and read their ledger-derived balances")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account, with its balance computed from ledger entries")
    public AccountResponse getAccount(@PathVariable UUID id) {
        return accountService.getAccount(id);
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "List the ledger entries for an account, in posting order")
    public List<LedgerEntryResponse> getEntries(@PathVariable UUID id) {
        return accountService.getEntries(id);
    }
}
