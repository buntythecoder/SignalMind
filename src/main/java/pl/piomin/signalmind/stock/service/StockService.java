package pl.piomin.signalmind.stock.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.signalmind.common.exception.ResourceNotFoundException;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public List<Stock> findAllActive() {
        return stockRepository.findByActiveTrueOrderBySymbolAsc();
    }

    public List<Stock> findByIndex(IndexType indexType) {
        return stockRepository.findByIndexTypeAndActiveTrueOrderBySymbolAsc(indexType);
    }

    public Stock findBySymbol(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", symbol));
    }

    public Stock findById(Long id) {
        return stockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", id));
    }

    public long countActive() {
        return stockRepository.countActive();
    }

    @Transactional
    public Stock updateTokens(String symbol, String angelToken, String breezeCode) {
        Stock stock = findBySymbol(symbol);
        stock.setAngelToken(angelToken);
        stock.setBreezeCode(breezeCode);
        return stockRepository.save(stock);
    }
}
