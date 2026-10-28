package com.naj.ledger.entity;

/**
 * Double-entry direction for an asset account:
 * a DEBIT increases the account balance, a CREDIT decreases it.
 * Every transfer therefore posts one DEBIT (receiver) and one CREDIT (sender)
 * for the same amount, so the two entries always sum to zero.
 */
public enum EntryType {
    DEBIT,
    CREDIT
}
