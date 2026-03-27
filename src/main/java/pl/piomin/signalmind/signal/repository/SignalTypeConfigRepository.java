package pl.piomin.signalmind.signal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.domain.SignalTypeConfig;

/**
 * Data access for {@link SignalTypeConfig} feature-flag records (SM-27).
 */
public interface SignalTypeConfigRepository extends JpaRepository<SignalTypeConfig, SignalType> {
}
