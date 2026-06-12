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
public class DeadInstanceCleanupScheduler {

    private final ExecutorInstanceService executorInstanceService;

    @Value("${datafactory.executor.instance-id:}")
    private String instanceId;

    /** 每30秒检测死实例 */
    @Scheduled(fixedRate = 30000)
    public void cleanup() {
        int dead = executorInstanceService.markDeadInstances();
        if (dead > 0 && instanceId != null) {
            int onlineCount = executorInstanceService.getOnlineCount();
            int mySlot = executorInstanceService.getMySlotIndex(instanceId);
            log.info("Dead instances cleaned: {}, online: {}, my slot: {}",
                    dead, onlineCount, mySlot);
        }
    }
}
