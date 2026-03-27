package pl.piomin.signalmind.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA / transaction configuration.
 *
 * <p>Note: {@code @EnableJpaAuditing} lives on {@link pl.piomin.signalmind.SignalMindApplication}
 * so it is always processed after Spring Boot's JPA auto-configuration. Unit tests that exclude
 * JPA must add {@code @MockBean JpaMetamodelMappingContext} to suppress the auditing context.
 *
 * <p>HikariCP datasource settings come from {@code application.yml}.
 */
@Configuration
@EnableTransactionManagement
public class JpaConfig {
}
