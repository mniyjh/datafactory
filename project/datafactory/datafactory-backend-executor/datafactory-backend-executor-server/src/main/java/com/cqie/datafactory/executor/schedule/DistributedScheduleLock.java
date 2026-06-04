package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.executor.schedule.entity.ScheduleLock;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleLockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 基于 MySQL INSERT 原子性的轻量分布式锁。
 * 不引入 Redis/ZK 外部依赖，利用主键唯一约束实现互斥。
 */
@Component
public class DistributedScheduleLock {

    private static final Logger log = LoggerFactory.getLogger(DistributedScheduleLock.class);

    private final ScheduleLockMapper lockMapper;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public DistributedScheduleLock(ScheduleLockMapper lockMapper) {
        this.lockMapper = lockMapper;
    }

    /**
     * 尝试获取调度锁。
     *
     * @param jobId       调度任务 ID
     * @param lockSeconds 锁持有时间（秒），应 ≥ 任务预期执行时间
     * @return true=获取成功，false=已被其他实例持有
     */
    @Transactional
    public boolean tryLock(Long jobId, int lockSeconds) {
        String lockKey = "job_" + jobId;
        LocalDateTime now = LocalDateTime.now();

        // 清理过期锁（防止死锁）
        lockMapper.delete(new LambdaQueryWrapper<ScheduleLock>()
                .eq(ScheduleLock::getLockKey, lockKey)
                .lt(ScheduleLock::getExpireAt, now));

        ScheduleLock lock = new ScheduleLock();
        lock.setLockKey(lockKey);
        lock.setHolder(instanceId);
        lock.setAcquireAt(now);
        lock.setExpireAt(now.plusSeconds(lockSeconds));

        try {
            lockMapper.insert(lock);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("DistributedLock: job {} 已被其他实例持有", jobId);
            return false;
        }
    }

    /**
     * 释放锁。
     */
    @Transactional
    public void release(Long jobId) {
        lockMapper.deleteById("job_" + jobId);
    }

    /**
     * 续期锁（用于长时间执行的任务）。
     */
    @Transactional
    public boolean renew(Long jobId, int extraSeconds) {
        String lockKey = "job_" + jobId;
        ScheduleLock lock = lockMapper.selectById(lockKey);
        if (lock == null || !instanceId.equals(lock.getHolder())) {
            return false;
        }
        lock.setExpireAt(LocalDateTime.now().plusSeconds(extraSeconds));
        lockMapper.updateById(lock);
        return true;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
