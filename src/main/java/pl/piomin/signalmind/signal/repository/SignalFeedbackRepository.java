package pl.piomin.signalmind.signal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.piomin.signalmind.signal.domain.SignalFeedback;

/**
 * Data access for {@link SignalFeedback} entities (SM-29).
 */
public interface SignalFeedbackRepository extends JpaRepository<SignalFeedback, Long> {
}
