-- Accounts table (feed type: accounts)
CREATE TABLE IF NOT EXISTS accounts (
    id          BIGSERIAL PRIMARY KEY,
    account_id  VARCHAR(100)   NOT NULL,
    email       VARCHAR(255)   NOT NULL,
    first_name  VARCHAR(100)   NOT NULL,
    last_name   VARCHAR(100)   NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    date_of_birth DATE,
    phone       VARCHAR(30),
    credit_limit NUMERIC(15, 2),
    currency    VARCHAR(3)     NOT NULL DEFAULT 'USD',
    row_hash    VARCHAR(64)    NOT NULL,
    batch_id    VARCHAR(255)   NOT NULL,
    feed_type   VARCHAR(50)    NOT NULL DEFAULT 'accounts',
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_row_hash   UNIQUE (row_hash),
    CONSTRAINT uq_accounts_account_id UNIQUE (account_id)
);

CREATE INDEX IF NOT EXISTS idx_accounts_batch_id ON accounts (batch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_email    ON accounts (email);
CREATE INDEX IF NOT EXISTS idx_accounts_status   ON accounts (status);

-- Transactions table (feed type: transactions)
CREATE TABLE IF NOT EXISTS transactions (
    id               BIGSERIAL PRIMARY KEY,
    transaction_id   VARCHAR(100)   NOT NULL,
    account_id       VARCHAR(100)   NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL DEFAULT 'USD',
    transaction_type VARCHAR(20)    NOT NULL,
    transaction_date DATE           NOT NULL,
    description      VARCHAR(500),
    row_hash         VARCHAR(64)    NOT NULL,
    batch_id         VARCHAR(255)   NOT NULL,
    feed_type        VARCHAR(50)    NOT NULL DEFAULT 'transactions',
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transactions_row_hash       UNIQUE (row_hash),
    CONSTRAINT uq_transactions_transaction_id UNIQUE (transaction_id)
);

CREATE INDEX IF NOT EXISTS idx_transactions_batch_id   ON transactions (batch_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions (account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date       ON transactions (transaction_date);

-- Dead-letter store: every skipped record lands here with full context
CREATE TABLE IF NOT EXISTS dead_letter_records (
    id                BIGSERIAL PRIMARY KEY,
    batch_id          VARCHAR(255)  NOT NULL,
    feed_type         VARCHAR(50)   NOT NULL,
    raw_record        TEXT          NOT NULL,
    failure_reason    VARCHAR(100)  NOT NULL,
    failure_field     VARCHAR(100),
    failure_message   TEXT          NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    reprocessed_at    TIMESTAMP,
    reprocess_batch_id VARCHAR(255),
    status            VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_dl_batch_id       ON dead_letter_records (batch_id);
CREATE INDEX IF NOT EXISTS idx_dl_feed_type      ON dead_letter_records (feed_type);
CREATE INDEX IF NOT EXISTS idx_dl_failure_reason ON dead_letter_records (failure_reason);
CREATE INDEX IF NOT EXISTS idx_dl_status         ON dead_letter_records (status);

-- Per-batch observability metadata
CREATE TABLE IF NOT EXISTS batch_run_metadata (
    id                BIGSERIAL PRIMARY KEY,
    batch_id          VARCHAR(255)  NOT NULL UNIQUE,
    feed_type         VARCHAR(50)   NOT NULL,
    source_file       VARCHAR(500)  NOT NULL,
    total_records     BIGINT        NOT NULL DEFAULT 0,
    succeeded         BIGINT        NOT NULL DEFAULT 0,
    failed            BIGINT        NOT NULL DEFAULT 0,
    skipped           BIGINT        NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
    started_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP,
    duration_ms       BIGINT,
    failure_breakdown JSONB
);

CREATE INDEX IF NOT EXISTS idx_brm_feed_type   ON batch_run_metadata (feed_type);
CREATE INDEX IF NOT EXISTS idx_brm_status      ON batch_run_metadata (status);
CREATE INDEX IF NOT EXISTS idx_brm_started_at  ON batch_run_metadata (started_at);
