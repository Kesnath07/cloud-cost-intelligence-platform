package com.cloudcost.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All dates and timestamps in the platform are UTC. AWS Cost Explorer reports usage
 * dates in UTC, so "today" must be evaluated in the same timezone.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
