package com.cqie.datafactory.configuration.schedule;

import com.cqie.datafactory.configuration.mapper.TokenBlacklistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final TokenBlacklistMapper tokenBlacklistMapper;

    /** 每小时清理一次过期token */
    @Scheduled(fixedRate = 3600000)
    public void cleanExpired() {
        int deleted = tokenBlacklistMapper.deleteExpired();
        if (deleted > 0) {
            log.info("Cleaned {} expired blacklisted tokens", deleted);
        }
    }
}
