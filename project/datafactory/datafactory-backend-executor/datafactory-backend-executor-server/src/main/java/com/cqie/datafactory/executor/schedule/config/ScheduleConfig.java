package com.cqie.datafactory.executor.schedule.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HighFrequencySchedulerProperties.class)
public class ScheduleConfig {
}
