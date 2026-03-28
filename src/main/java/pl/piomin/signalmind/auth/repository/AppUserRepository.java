package pl.piomin.signalmind.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.piomin.signalmind.auth.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);
}
