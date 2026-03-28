package pl.piomin.signalmind.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.piomin.signalmind.auth.domain.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUserId(Long userId);
}
