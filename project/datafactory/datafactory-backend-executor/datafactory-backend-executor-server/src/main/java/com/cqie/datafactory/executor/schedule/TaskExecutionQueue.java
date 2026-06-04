package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 任务执行排队器，支持 block_strategy=QUEUE 策略。
 * 每个 job 维护独立的有限阻塞队列，超出容量时拒绝。
 */
@Component
public class TaskExecutionQueue {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionQueue.class);

    private final ConcurrentHashMap<Long, LinkedBlockingQueue<ScheduleJob>> queues = new ConcurrentHashMap<>();

    /**
     * 将任务放入排队队列。
     *
     * @param job     待执行的任务
     * @param maxSize 队列容量上限
     * @return true=入队成功，false=队列已满
     */
    public boolean enqueue(ScheduleJob job, int maxSize) {
        LinkedBlockingQueue<ScheduleJob> queue = queues.computeIfAbsent(
                job.getId(), k -> new LinkedBlockingQueue<>(maxSize));
        boolean offered = queue.offer(job);
        if (!offered) {
            log.warn("任务 {} 排队队列已满 (容量={}), 丢弃", job.getId(), maxSize);
        } else {
            log.debug("任务 {} 已入队 (队列深度={})", job.getId(), queue.size());
        }
        return offered;
    }

    /**
     * 从排队队列取出下一个待执行的任务。
     */
    public ScheduleJob dequeue(Long jobId) {
        LinkedBlockingQueue<ScheduleJob> queue = queues.get(jobId);
        return queue != null ? queue.poll() : null;
    }

    /**
     * 获取当前排队深度。
     */
    public int getQueueDepth(Long jobId) {
        LinkedBlockingQueue<ScheduleJob> queue = queues.get(jobId);
        return queue != null ? queue.size() : 0;
    }

    /**
     * 清理 job 的排队队列（job 被删除时调用）。
     */
    public void clear(Long jobId) {
        LinkedBlockingQueue<ScheduleJob> queue = queues.remove(jobId);
        if (queue != null) {
            log.info("已清理任务 {} 的排队队列 (丢弃 {} 个等待任务)", jobId, queue.size());
        }
    }
}
