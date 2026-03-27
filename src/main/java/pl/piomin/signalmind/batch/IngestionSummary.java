package pl.piomin.signalmind.batch;

/**
 * Result returned by {@link CandleBackfillProcessor} for one stock.
 * Written to the job log by {@link IngestionSummaryWriter}.
 */
public record IngestionSummary(
        String  symbol,
        int     candlesInserted,
        boolean success,
        String  errorMessage
) {
    static IngestionSummary ok(String symbol, int inserted) {
        return new IngestionSummary(symbol, inserted, true, null);
    }

    static IngestionSummary failed(String symbol, String error) {
        return new IngestionSummary(symbol, 0, false, error);
    }

    @Override
    public String toString() {
        if (success) {
            return "[OK]  " + symbol + " — " + candlesInserted + " candles inserted";
        }
        return "[ERR] " + symbol + " — " + errorMessage;
    }
}
