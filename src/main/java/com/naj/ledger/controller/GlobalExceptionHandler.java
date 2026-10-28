package com.naj.ledger.controller;

import com.naj.ledger.dto.ErrorResponse;
import com.naj.ledger.exception.BadRequestException;
import com.naj.ledger.exception.IdempotencyConflictException;
import com.naj.ledger.exception.InsufficientFundsException;
import com.naj.ledger.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return status(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(BadRequestException e) {
        return status(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> insufficientFunds(InsufficientFundsException e) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_funds", e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> idempotencyConflict(IdempotencyConflictException e) {
        return status(HttpStatus.CONFLICT, "idempotency_conflict", e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException e) {
        return status(HttpStatus.BAD_REQUEST, "bad_request",
                "Required header is missing: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return status(HttpStatus.BAD_REQUEST, "validation_error", detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataIntegrity(DataIntegrityViolationException e) {
        // Most commonly a concurrent request winning the same idempotency key.
        // A retry then returns the original transfer through the idempotency check.
        return status(HttpStatus.CONFLICT, "conflict",
                "Request conflicts with an existing record (for example, a reused idempotency key)");
    }

    private ResponseEntity<ErrorResponse> status(HttpStatus httpStatus, String error, String message) {
        return ResponseEntity.status(httpStatus).body(new ErrorResponse(error, message));
    }
}
