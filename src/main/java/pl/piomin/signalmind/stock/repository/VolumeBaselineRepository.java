package pl.piomin.signalmind.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.domain.VolumeBaseline;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link VolumeBaseline} entities.
 *
 * <p>The {@code deleteAllByStock} method is annotated {@code @Modifying @Transactional}
 * so Spring Data executes a bulk DELETE rather than loading entities first.
 */
@Repository
public interface VolumeBaselineRepository extends JpaRepository<VolumeBaseline, Long> {

    /**
     * Returns all baselines for a stock, ordered by slot time ascending
     * (earliest market slot first).
     */
    List<VolumeBaseline> findByStockOrderBySlotTimeAsc(Stock stock);

    /**
     * Looks up the baseline for a specific stock and minute slot.
     */
    Optional<VolumeBaseline> findByStockAndSlotTime(Stock stock, LocalTime slotTime);

    /**
     * Bulk-deletes all baselines belonging to {@code stock}.
     * Called before a full rebuild so the table is replaced atomically
     * within the enclosing transaction in
     * {@link pl.piomin.signalmind.stock.service.VolumeBaselineService#recalculate}.
     */
    @Modifying
    @Transactional
    void deleteAllByStock(Stock stock);
}
