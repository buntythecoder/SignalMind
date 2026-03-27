-- SM-26: Confidence Scoring Engine — audit trail columns
-- Each factor score is stored alongside the final confidence score so that
-- the scoring breakdown can be inspected offline for model improvement.
-- All columns are nullable: NULL means the signal pre-dates SM-26 scoring.

ALTER TABLE signals
    ADD COLUMN score_base        INTEGER,
    ADD COLUMN score_volume      INTEGER,
    ADD COLUMN score_time_of_day INTEGER,
    ADD COLUMN score_regime      INTEGER,
    ADD COLUMN score_win_rate    INTEGER,
    ADD COLUMN score_confluence  INTEGER;
