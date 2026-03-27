package pl.piomin.signalmind.signal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.domain.SignalTypeConfig;
import pl.piomin.signalmind.signal.repository.SignalTypeConfigRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SignalTypeConfigService} (SM-27).
 */
class SignalTypeConfigServiceTest {

    private SignalTypeConfigRepository mockRepo;
    private SignalTypeConfigService service;

    @BeforeEach
    void setUp() {
        mockRepo = mock(SignalTypeConfigRepository.class);
        service  = new SignalTypeConfigService(mockRepo);
    }

    private static SignalTypeConfig config(SignalType type, boolean enabled) {
        return new SignalTypeConfig(type, enabled);
    }

    @Test
    @DisplayName("isEnabled returns true for an enabled type in cache")
    void isEnabled_true_whenEnabled() {
        when(mockRepo.findAll()).thenReturn(List.of(config(SignalType.ORB, true)));
        service.loadOnStartup();

        assertThat(service.isEnabled(SignalType.ORB)).isTrue();
    }

    @Test
    @DisplayName("isEnabled returns false for a disabled type in cache")
    void isEnabled_false_whenDisabled() {
        when(mockRepo.findAll()).thenReturn(List.of(config(SignalType.ORB, false)));
        service.loadOnStartup();

        assertThat(service.isEnabled(SignalType.ORB)).isFalse();
    }

    @Test
    @DisplayName("isEnabled defaults to true when type not in cache (fail-open)")
    void isEnabled_defaultsToTrue_whenNotInCache() {
        when(mockRepo.findAll()).thenReturn(List.of()); // empty cache
        service.loadOnStartup();

        assertThat(service.isEnabled(SignalType.VWAP_BREAKOUT)).isTrue();
    }

    @Test
    @DisplayName("refresh updates the cache with new values from the repository")
    void refresh_updatesCache() {
        when(mockRepo.findAll())
                .thenReturn(List.of(config(SignalType.ORB, true)))   // initial
                .thenReturn(List.of(config(SignalType.ORB, false)));  // after refresh

        service.loadOnStartup();
        assertThat(service.isEnabled(SignalType.ORB)).isTrue();

        service.refresh(); // second call → ORB disabled
        assertThat(service.isEnabled(SignalType.ORB)).isFalse();
    }

    @Test
    @DisplayName("refresh retains stale cache when repository throws")
    void refresh_retainsStaleCache_onException() {
        when(mockRepo.findAll())
                .thenReturn(List.of(config(SignalType.ORB, true)))
                .thenThrow(new RuntimeException("DB unavailable"));

        service.loadOnStartup();
        service.refresh(); // second call throws — should not clear cache

        assertThat(service.isEnabled(SignalType.ORB)).isTrue();
    }

    @Test
    @DisplayName("getCache returns a snapshot of all loaded entries")
    void getCache_returnsSnapshot() {
        when(mockRepo.findAll()).thenReturn(List.of(
                config(SignalType.ORB, true),
                config(SignalType.GAP_FILL_LONG, false)
        ));
        service.loadOnStartup();

        Map<SignalType, Boolean> snapshot = service.getCache();
        assertThat(snapshot).containsEntry(SignalType.ORB, true)
                             .containsEntry(SignalType.GAP_FILL_LONG, false);
    }

    @Test
    @DisplayName("cache is populated with all 7 seeded signal types")
    void cache_allSeededTypes() {
        List<SignalTypeConfig> allTypes = List.of(
                config(SignalType.ORB, true),
                config(SignalType.VWAP_BREAKOUT, true),
                config(SignalType.VWAP_BREAKDOWN, true),
                config(SignalType.RSI_OVERSOLD_BOUNCE, true),
                config(SignalType.RSI_OVERBOUGHT_REJECTION, true),
                config(SignalType.GAP_FILL_LONG, true),
                config(SignalType.GAP_FILL_SHORT, true)
        );
        when(mockRepo.findAll()).thenReturn(allTypes);
        service.loadOnStartup();

        for (SignalType type : SignalType.values()) {
            if (type == SignalType.RSI_REVERSAL) continue; // not seeded
            assertThat(service.isEnabled(type))
                    .as("type %s should be enabled", type)
                    .isTrue();
        }
    }
}
