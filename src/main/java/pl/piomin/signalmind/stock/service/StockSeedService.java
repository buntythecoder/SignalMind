package pl.piomin.signalmind.stock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Seeds the stocks table from data/stocks.json on first startup.
 * Idempotent: skips symbols already present.
 */
@Service
public class StockSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockSeedService.class);

    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    public StockSeedService(StockRepository stockRepository, ObjectMapper objectMapper) {
        this.stockRepository = stockRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (stockRepository.countActive() > 0) {
            log.info("Stock universe already seeded ({} stocks). Skipping.", stockRepository.countActive());
            return;
        }

        ClassPathResource resource = new ClassPathResource("data/stocks.json");
        if (!resource.exists()) {
            log.warn("data/stocks.json not found — stock seeding skipped");
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Map<String, String>> entries = objectMapper.readValue(is, new TypeReference<>() {});
            int seeded = 0;
            for (Map<String, String> entry : entries) {
                String symbol = entry.get("symbol");
                if (stockRepository.existsBySymbol(symbol)) {
                    continue;
                }
                Stock stock = new Stock(
                        symbol,
                        entry.get("companyName"),
                        IndexType.valueOf(entry.get("indexType"))
                );
                stock.setBreezeCode(entry.get("breezeCode"));
                stock.setAngelToken(entry.get("angelToken"));
                stockRepository.save(stock);
                seeded++;
            }
            log.info("Stock seed complete: {} stocks inserted.", seeded);
        }
    }
}
