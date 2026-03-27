package pl.piomin.signalmind.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findByActiveTrueOrderBySymbolAsc();

    List<Stock> findByIndexTypeAndActiveTrueOrderBySymbolAsc(IndexType indexType);

    boolean existsBySymbol(String symbol);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.angelToken IS NOT NULL ORDER BY s.symbol")
    List<Stock> findAllActiveWithAngelToken();

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.active = true")
    long countActive();
}
