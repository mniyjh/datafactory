package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CronExpressionTest {

    @Test
    void shouldCalculateNextFireTimeFromCronExpression() {
        String cronExpr = "0 0 9 * * ?";
        CronExpression cron = CronExpression.parse(cronExpr);
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 8, 0);
        LocalDateTime next = cron.next(now);
        assertNotNull(next);
        assertEquals(9, next.getHour());
        assertEquals(0, next.getMinute());
    }

    @Test
    void shouldHandleInvalidCronGracefully() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("invalid cron"));
    }

    @Test
    void shouldReturnNullForBlankCron() {
        ScheduleJobService service = new ScheduleJobService();
        ScheduleJob job = new ScheduleJob();
        job.setCronExpression(null);
        service.computeNextFireTime(job);
        assertNull(job.getNextFireTime());

        job.setCronExpression("");
        service.computeNextFireTime(job);
        assertNull(job.getNextFireTime());
    }

    @Test
    void shouldSetNextFireTimeForValidCron() {
        ScheduleJobService service = new ScheduleJobService();
        ScheduleJob job = new ScheduleJob();
        job.setCronExpression("0 0 12 * * ?");
        service.computeNextFireTime(job);
        assertNotNull(job.getNextFireTime());
    }

    @Test
    void shouldBeRepeatable() {
        String cronExpr = "*/5 * * * * ?";
        CronExpression cron = CronExpression.parse(cronExpr);
        LocalDateTime t1 = cron.next(LocalDateTime.now());
        assertNotNull(t1);
        LocalDateTime t2 = cron.next(t1);
        assertNotNull(t2);
        assertTrue(t2.isAfter(t1));
    }
}
