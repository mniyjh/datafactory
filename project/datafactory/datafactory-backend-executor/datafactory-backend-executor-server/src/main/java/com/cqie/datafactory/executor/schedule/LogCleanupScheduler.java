package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.service.ExecutionLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled log cleanup to prevent execution_log table bloat
 * from high-frequency scheduled tasks.
 */
@Component
public class LogCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupScheduler.class);

    private final ExecutionLogService executionLogService;

    @Value("${datafactory.scheduler.log-cleanup.retention-days:7}")
    private int retentionDays;

    public LogCleanupScheduler(ExecutionLogService executionLogService) {
        this.executionLogService = executionLogService;
    }

    @Scheduled(cron = "${datafactory.scheduler.log-cleanup.cron:0 0 * * * ?}")
    public void cleanup() {
        try {
            int deleted = executionLogService.cleanupOldLogs(retentionDays);
            log.info("Log cleanup completed: {} rows deleted (retention: {} days)", deleted, retentionDays);
        } catch (Exception e) {
            log.error("Log cleanup failed", e);
        }
    }
}
