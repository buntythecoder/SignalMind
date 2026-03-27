package pl.piomin.signalmind.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.service.StockService;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@Tag(name = "Stocks", description = "NSE/BSE stock universe management")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    @Operation(summary = "List all active stocks", description = "Returns all active stocks, optionally filtered by index")
    public ResponseEntity<List<Stock>> listStocks(
            @RequestParam(required = false) IndexType index) {
        List<Stock> stocks = (index != null)
                ? stockService.findByIndex(index)
                : stockService.findAllActive();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Get stock by symbol")
    public ResponseEntity<Stock> getBySymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.findBySymbol(symbol.toUpperCase()));
    }

    @GetMapping("/count")
    @Operation(summary = "Count active stocks in universe")
    public ResponseEntity<Long> countActive() {
        return ResponseEntity.ok(stockService.countActive());
    }
}
