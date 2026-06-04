package com.cqie.datafactory.executor.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("schedule_lock")
public class ScheduleLock {
    @TableId
    private String lockKey;
    private String holder;
    private LocalDateTime acquireAt;
    private LocalDateTime expireAt;

    public String getLockKey() { return lockKey; }
    public void setLockKey(String lockKey) { this.lockKey = lockKey; }

    public String getHolder() { return holder; }
    public void setHolder(String holder) { this.holder = holder; }

    public LocalDateTime getAcquireAt() { return acquireAt; }
    public void setAcquireAt(LocalDateTime acquireAt) { this.acquireAt = acquireAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
}
