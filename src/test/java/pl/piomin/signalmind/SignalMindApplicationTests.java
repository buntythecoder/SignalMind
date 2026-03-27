package pl.piomin.signalmind;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import pl.piomin.signalmind.market.repository.MarketHolidayRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;

@SpringBootTest
@ActiveProfiles("test")
class SignalMindApplicationTests {

    // Mock JPA repositories so the context loads without a real database.
    // Integration tests that exercise real DB queries use Testcontainers (see SM-11).
    @MockBean
    StockRepository stockRepository;

    @MockBean
    MarketHolidayRepository marketHolidayRepository;

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts without errors.
    }
}
