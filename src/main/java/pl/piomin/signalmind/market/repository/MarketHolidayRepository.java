package pl.piomin.signalmind.market.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.piomin.signalmind.market.domain.MarketHoliday;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, Long> {

    Optional<MarketHoliday> findByHolidayDate(LocalDate date);

    boolean existsByHolidayDate(LocalDate date);
}
