package pl.piomin.signalmind.integration.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a fallback {@link NoOpTelegramAlertService} when no real
 * {@link TelegramAlertService} bean (e.g. {@link TelegramBotAlertService})
 * is present in the application context.
 *
 * <p>Using a {@code @Configuration} class with {@code @Bean} ensures
 * {@code @ConditionalOnMissingBean} is evaluated after all component-scanned
 * beans are registered — which is the correct and reliable way to use this
 * annotation (as opposed to placing it directly on a {@code @Service}).
 */
@Configuration
public class TelegramAlertConfiguration {

    @Bean
    @ConditionalOnMissingBean(TelegramAlertService.class)
    public TelegramAlertService noOpTelegramAlertService() {
        return new NoOpTelegramAlertService();
    }
}
