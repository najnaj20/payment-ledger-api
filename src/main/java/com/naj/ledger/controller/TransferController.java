package com.naj.ledger.controller;

import com.naj.ledger.dto.CreateTransferRequest;
import com.naj.ledger.dto.TransferResponse;
import com.naj.ledger.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
@Tag(name = "Transfers", description = "Idempotent double-entry transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a transfer. Replaying the same Idempotency-Key "
            + "with the same payload returns the original transfer; "
            + "reusing the key with a different payload returns 409.")
    public TransferResponse createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @Parameter(description = "Client-generated unique key for this transfer attempt", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return transferService.createTransfer(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transfer by id")
    public TransferResponse getTransfer(@PathVariable UUID id) {
        return transferService.getTransfer(id);
    }
}
