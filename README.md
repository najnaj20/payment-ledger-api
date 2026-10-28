# Payment Ledger API

A Spring Boot service for recording money movement with double-entry
bookkeeping. It is deliberately small: the point is to get the money
invariants right, not to cover every payment feature.

## Design

### The ledger is the source of truth

There is no mutable `balance` column on an account. An account's balance
is always `sum(DEBIT) - sum(CREDIT)` over its ledger entries, so a
balance can never drift from the entries that produced it. Ledger rows
are append-only: nothing in the API updates or deletes an entry.

### One transfer, exactly two entries

Every transfer posts, in a single database transaction:

- one CREDIT on the sender (an asset account decreases), and
- one DEBIT on the receiver (an asset account increases),

for the same amount. A defensive check inside the transaction verifies
`debit total == credit total` before committing. If anything fails, the
whole transaction rolls back: the transfer row, both entries, and the
idempotency record all disappear together, so a transfer can never leave
a single, unbalanced entry behind.

Amounts are stored as `BIGINT` minor units (for example, cents/sen).
Money is never stored as a float or double.

### Idempotency

`POST /transfers` requires an `Idempotency-Key` header. The key is
stored with a SHA-256 hash of the canonical payload, under a unique
database constraint on the key:

- Same key + same payload: the original transfer is returned, with no
  second transfer and no second pair of entries.
- Same key + different payload: `409 Conflict`. A key must not be
  reused for a different transfer.
- A concurrent request that loses the race for the same key gets `409`;
  retrying it then returns the original transfer through the check above.

Keys are scoped to the transfer attempt, so clients should generate a
fresh UUID per intended transfer and reuse it only for retries of that
same transfer.

### Concurrency

A transfer locks both account rows with pessimistic write locks, in a
consistent order (sorted by account id), and holds those locks until
commit. Two transfers touching the same account are therefore
serialised for the balance check, and two transfers between the same
pair of accounts can never deadlock by locking in opposite orders.

## API

| Method | Endpoint | Description |
| --- | --- | --- |
| POST | `/accounts` | Create an account |
| GET | `/accounts/{id}` | Account with ledger-derived balance |
| GET | `/accounts/{id}/entries` | Ledger entries for an account |
| POST | `/transfers` | Create a transfer (requires `Idempotency-Key`) |
| GET | `/transfers/{id}` | Transfer by id |

The full contract is in `openapi.yaml`. When the application is running,
the live contract is served at `/v3/api-docs` and interactive docs are
at `/swagger-ui.html`.

Example: create a funded sender, then transfer from it. A new account
starts at zero unless an opening balance is given, and that opening
balance is itself posted as a balanced pair (CREDIT external, DEBIT
new account), never as a one-sided entry.

```bash
curl -X POST http://localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -d '{"owner":"Alya","currency":"IDR","initialBalanceMinor":500000}'

curl -X POST http://localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -d '{"owner":"Bima","currency":"IDR"}'
```

Example transfer:

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000' \
  -d '{"fromAccountId":"<sender-id>","toAccountId":"<receiver-id>","amountMinor":100000,"currency":"IDR"}'
```

## Failure scenarios

| Scenario | Expected behaviour |
| --- | --- |
| Client retries a transfer (same key, same payload) | Original transfer returned, balance changes once |
| Same key, different amount or accounts | `409 Conflict`, nothing new is recorded |
| Sender has insufficient funds | `422`, no transfer and no entry is persisted |
| Transfer between an account and itself, mismatched currency, or an inactive/missing account | `400` / `404`, nothing is persisted |
| Concurrent transfers that together exceed the balance | Only transfers covered by the balance succeed; the rest get `422` and the final balance is never negative |
| Crash mid-transaction | The database transaction rolls back, so no partial entry survives; the client retries with the same key |

## Run locally

Prerequisites: Java 21, Maven, Docker (for the database via Compose,
or a local PostgreSQL).

```bash
# Start PostgreSQL and the app (set a local-only database password first)
export POSTGRES_PASSWORD=<your-local-password>
docker compose up --build

# Or, with a local PostgreSQL on port 5432:
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ledger
export SPRING_DATASOURCE_USERNAME=ledger
export SPRING_DATASOURCE_PASSWORD=<your-local-password>
mvn spring-boot:run
```

Flyway applies the schema in `src/main/resources/db/migration` on
startup. Hibernate is set to `validate` only: it never creates or
alters tables.

## Tests

```bash
mvn test
```

The test suite runs against PostgreSQL in Testcontainers and covers:
balanced debit/credit posting, idempotent retry, different-payload
conflict, insufficient-funds rollback, and concurrent transfers that
must never overdraw an account.

## Deploy to Kubernetes

```bash
# Create the real secret first (the file in k8s/ is a placeholder template)
kubectl create secret generic payment-ledger-api-secret \
  --from-literal=SPRING_DATASOURCE_USERNAME=<db-user> \
  --from-literal=SPRING_DATASOURCE_PASSWORD=<db-password>

kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

The Deployment runs 2 replicas with CPU/memory limits, a liveness
probe on `/actuator/health/liveness`, and a readiness probe on
`/actuator/health/readiness`. Readiness includes the database health
check, so a pod does not receive traffic before it can reach the
database. Non-secret configuration lives in the ConfigMap; database
credentials live only in the Secret.

## Stack

Java 21, Spring Boot 3, Spring Data JPA / Hibernate, Flyway,
PostgreSQL, springdoc-openapi, Spring Actuator, Testcontainers.
