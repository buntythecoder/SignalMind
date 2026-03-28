-- SM-34: Extend users table for onboarding flow
-- Adds name, phone, disclaimer acceptance, Telegram verification,
-- onboarding step tracking, and email verification fields.

ALTER TABLE users
    ADD COLUMN name                       VARCHAR(100),
    ADD COLUMN phone                      VARCHAR(20),
    ADD COLUMN disclaimer_accepted        BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN disclaimer_accepted_at     TIMESTAMPTZ,
    ADD COLUMN telegram_chat_id           VARCHAR(50),
    ADD COLUMN telegram_verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN onboarding_step            INT          NOT NULL DEFAULT 0,
    ADD COLUMN email_verified             BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verification_token   VARCHAR(255),
    ADD COLUMN email_verification_sent_at TIMESTAMPTZ;

CREATE UNIQUE INDEX idx_users_ev_token ON users (email_verification_token)
    WHERE email_verification_token IS NOT NULL;
