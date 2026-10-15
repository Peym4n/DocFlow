package at.fhtw.docflow.configuration;

import at.fhtw.docflow.identity.application.CurrentOwner;
import at.fhtw.docflow.identity.persistence.UserEntity;
import at.fhtw.docflow.identity.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoOwnerConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DemoOwnerConfiguration.class);

    @Bean
    CurrentOwner demoCurrentOwner(UserRepository users) {
        long ownerId = users.findByUsername("demo")
                .orElseGet(() -> users.saveAndFlush(new UserEntity("demo"))).getId();
        log.warn("Demo profile active: all document requests use one local owner; authentication is disabled");
        return () -> ownerId;
    }
}
