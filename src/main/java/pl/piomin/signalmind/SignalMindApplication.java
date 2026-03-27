package pl.piomin.signalmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableJpaAuditing lives here (not in JpaConfig) so it is guaranteed to be
// processed after Spring Boot's JPA auto-configuration creates the
// EntityManagerFactory — avoiding the @ConditionalOnBean ordering trap.
// Unit tests that exclude JPA must @MockBean JpaMetamodelMappingContext.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableJpaAuditing
public class SignalMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalMindApplication.class, args);
    }
}
