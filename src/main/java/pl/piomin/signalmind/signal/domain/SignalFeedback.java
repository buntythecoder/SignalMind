package pl.piomin.signalmind.signal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Records a user's reaction to a dispatched signal (SM-29).
 *
 * <p>Persisted when a subscriber taps the "Took Trade" or "Watching" inline button
 * on a Telegram alert.  The {@code signal_id} is stored as a plain {@code Long}
 * (not a {@code @ManyToOne}) to avoid lazy-loading issues in the webhook callback handler.
 *
 * <p>A unique constraint ({@code uq_signal_feedback}) prevents duplicate feedback
 * entries for the same signal × chat combination.
 */
@Entity
@Table(name = "signal_feedback")
public class SignalFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Column(name = "chat_id", nullable = false, length = 50)
    private String chatId;

    @Column(name = "feedback_type", nullable = false, length = 20)
    private String feedbackType;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Required by JPA. Not for application use. */
    protected SignalFeedback() {
    }

    /**
     * Creates a new feedback entry, recording {@code Instant.now()} as the timestamp.
     *
     * @param signalId     ID of the signal the user reacted to
     * @param chatId       Telegram chat ID of the user (numeric string)
     * @param feedbackType one of {@code "TOOK_TRADE"} or {@code "WATCHING"}
     */
    public SignalFeedback(Long signalId, String chatId, String feedbackType) {
        this.signalId     = signalId;
        this.chatId       = chatId;
        this.feedbackType = feedbackType;
        this.recordedAt   = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSignalId() {
        return signalId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
