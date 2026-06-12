package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.service.ExecutorInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final ExecutorInstanceService executorInstanceService;

    @Value("${datafactory.executor.instance-id:}")
    private String instanceId;

    /** 每10秒发送心跳 */
    @Scheduled(fixedRate = 10000)
    public void heartbeat() {
        if (instanceId != null && !instanceId.isBlank()) {
            executorInstanceService.heartbeat(instanceId);
            log.debug("Heartbeat sent: {}", instanceId);
        }
    }
}
