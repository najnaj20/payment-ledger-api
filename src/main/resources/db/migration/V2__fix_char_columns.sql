-- V1 used CHAR(3) / CHAR(64), which PostgreSQL reports as bpchar.
-- JPA String fields validate as VARCHAR, so Hibernate validation failed
-- with "wrong column type ... found [bpchar], but expecting [varchar]".
-- This migration converts those columns to VARCHAR without changing
-- their values. V1 is intentionally left untouched: editing an already
-- applied migration would break Flyway's checksum check on existing
-- databases, while a new migration runs cleanly on both fresh and
-- existing databases.
ALTER TABLE accounts ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE transfers ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE ledger_entries ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE idempotency_keys ALTER COLUMN request_hash TYPE VARCHAR(64);
