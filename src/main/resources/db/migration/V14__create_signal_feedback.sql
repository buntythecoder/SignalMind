CREATE TABLE signal_feedback (
    id            BIGSERIAL    PRIMARY KEY,
    signal_id     BIGINT       NOT NULL REFERENCES signals(id) ON DELETE CASCADE,
    chat_id       VARCHAR(50)  NOT NULL,
    feedback_type VARCHAR(20)  NOT NULL,
    recorded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_signal_feedback UNIQUE (signal_id, chat_id),
    CONSTRAINT chk_feedback_type CHECK (feedback_type IN ('TOOK_TRADE', 'WATCHING'))
);
