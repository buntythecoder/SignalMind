package pl.piomin.signalmind.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.piomin.signalmind.auth.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    /** SM-34: Look up a user by their email-verification token (unique index guarantees at most one result). */
    Optional<AppUser> findByEmailVerificationToken(String emailVerificationToken);
}
