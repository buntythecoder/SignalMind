package pl.piomin.signalmind.onboarding.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import pl.piomin.signalmind.onboarding.domain.UserWatchlistEntry;
import pl.piomin.signalmind.onboarding.domain.UserWatchlistId;

import java.util.List;

/**
 * Repository for the user_watchlist table (SM-34).
 */
public interface UserWatchlistRepository extends JpaRepository<UserWatchlistEntry, UserWatchlistId> {

    /** Returns all watchlist entries for the given user. */
    List<UserWatchlistEntry> findByIdUserId(Long userId);

    /** Removes all watchlist entries for the given user (used when replacing the watchlist). */
    @Modifying
    @Transactional
    void deleteByIdUserId(Long userId);
}
