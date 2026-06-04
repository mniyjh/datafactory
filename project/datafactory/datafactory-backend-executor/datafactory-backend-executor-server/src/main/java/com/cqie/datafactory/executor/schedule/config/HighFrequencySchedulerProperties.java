package com.cqie.datafactory.executor.schedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datafactory.scheduler.high-frequency")
public class HighFrequencySchedulerProperties {

    /** Feature toggle for the high-frequency scheduler. */
    private boolean enabled = true;

    /** Thread pool size for the ScheduledExecutorService. */
    private int threadPoolSize = 4;

    /** Minimum allowed interval in seconds. Default 60 = 1 minute. */
    private int minIntervalSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getThreadPoolSize() { return threadPoolSize; }
    public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }

    public int getMinIntervalSeconds() { return minIntervalSeconds; }
    public void setMinIntervalSeconds(int minIntervalSeconds) { this.minIntervalSeconds = minIntervalSeconds; }
}
