package com.platform.api.config;

import com.platform.api.doubles.InMemoryAuditQueryPort;
import com.platform.api.doubles.InMemoryConfigPort;
import com.platform.api.doubles.InMemorySourceConnectionPort;
import com.platform.api.doubles.InMemorySubmissionPort;
import com.platform.api.doubles.InMemoryTransitionPort;
import com.platform.api.doubles.InMemoryWorkItemQueryPort;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TestApiConfig {

    @TestConfiguration
    static class Doubles {

        @Bean
        @Primary
        public InMemoryWorkItemQueryPort workItemQueryPort() {
            return new InMemoryWorkItemQueryPort();
        }

        @Bean
        @Primary
        public InMemoryTransitionPort transitionPort(InMemoryWorkItemQueryPort queryPort) {
            return new InMemoryTransitionPort(queryPort);
        }

        @Bean
        @Primary
        public InMemoryAuditQueryPort auditQueryPort() {
            return new InMemoryAuditQueryPort();
        }

        @Bean
        @Primary
        public InMemoryConfigPort configPort() {
            return new InMemoryConfigPort();
        }

        @Bean
        @Primary
        public InMemorySubmissionPort submissionPort() {
            return new InMemorySubmissionPort();
        }

        @Bean
        @Primary
        public InMemorySourceConnectionPort sourceConnectionPort() {
            return new InMemorySourceConnectionPort();
        }
    }
}
