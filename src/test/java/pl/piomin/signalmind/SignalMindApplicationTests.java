package pl.piomin.signalmind;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ActiveProfiles;
import pl.piomin.signalmind.market.repository.MarketHolidayRepository;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;
import pl.piomin.signalmind.stock.service.StockSeedService;

@SpringBootTest
@ActiveProfiles("test")
class SignalMindApplicationTests {

    // Mock JPA repositories — no real database in this profile.
    // Full integration tests (real DB + Flyway + seed) are in FlywayMigrationIT.
    @MockBean
    StockRepository stockRepository;

    @MockBean
    MarketHolidayRepository marketHolidayRepository;

    @MockBean
    CandleRepository candleRepository;

    // StockSeedService.run() is @Transactional; with JPA excluded there is no
    // PlatformTransactionManager, so the proxy creation fails. Mock the service
    // to prevent it from running in the no-JPA context-load test.
    @MockBean
    StockSeedService stockSeedService;

    // @EnableJpaAuditing on SignalMindApplication imports JpaMetamodelMappingContext,
    // which requires at least one JPA EntityManagerFactory. Suppress with a mock.
    @MockBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts without errors.
    }
}
