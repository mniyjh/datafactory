package com.cqie.datafactory.executor.schedule.util;

/**
 * Cron expression helper for detecting high-frequency (sub-minute) patterns.
 * Supports 6-field cron expressions (seconds minutes hours day month week).
 */
public final class CronHelper {

    private CronHelper() {}

    /**
     * Check if the cron expression triggers more than once per minute.
     * High-frequency means the seconds field is star or star-slash-N where N below 60.
     */
    public static boolean isHighFrequency(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return false;
        }
        String[] fields = cronExpression.trim().split("\\s+");
        if (fields.length < 6) {
            return false;
        }
        String seconds = fields[0];
        return isHighFrequencySeconds(seconds);
    }

    /**
     * Get the interval in seconds for a high-frequency cron expression.
     * Returns 1 for star, N for star-slash-N. Throws if not high-frequency.
     */
    public static long getIntervalSeconds(String cronExpression) {
        if (!isHighFrequency(cronExpression)) {
            throw new IllegalArgumentException("Not a high-frequency cron: " + cronExpression);
        }
        String seconds = cronExpression.trim().split("\\s+")[0];
        if ("*".equals(seconds)) {
            return 1;
        }
        return Long.parseLong(seconds.substring(2));
    }

    private static boolean isHighFrequencySeconds(String seconds) {
        if ("*".equals(seconds)) {
            return true;
        }
        if (seconds.startsWith("*/")) {
            try {
                long interval = Long.parseLong(seconds.substring(2));
                return interval > 0 && interval < 60;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}
