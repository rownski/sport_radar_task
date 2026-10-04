package org.rowny.config;

import org.rowny.domain.MatchRepository;
import org.rowny.persistence.JdbcMatchRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class JdbcPersistenceConfiguration {
    @Bean
    MatchRepository matchRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        return new JdbcMatchRepository(jdbc, transactionManager);
    }
}
