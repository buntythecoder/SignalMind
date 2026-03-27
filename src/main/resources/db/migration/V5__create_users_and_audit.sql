-- V5: User accounts and audit log

CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER','ADMIN'))
);

CREATE INDEX idx_users_email ON users (email);

COMMENT ON TABLE users IS 'Application users. Passwords stored as bcrypt hashes.';

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    event_type   VARCHAR(50)  NOT NULL,
    entity_type  VARCHAR(50),
    entity_id    VARCHAR(50),
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_event   ON audit_log (event_type, created_at DESC);
CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);

COMMENT ON TABLE audit_log IS 'System-level audit events: logins, signal dispatches, API errors, etc.';
