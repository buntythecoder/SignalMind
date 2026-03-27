package pl.piomin.signalmind.common.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing
@EnableTransactionManagement
public class JpaConfig {
    // Only activated when JPA EntityManagerFactory is present (skipped in unit tests).
    // @EnableJpaAuditing enables @CreatedDate / @LastModifiedDate on entities.
    // HikariCP datasource settings come from application.yml.
}
