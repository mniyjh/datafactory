package com.cqie.datafactory.executor.service;

import com.cqie.datafactory.executor.entity.ExecutorInstance;
import com.cqie.datafactory.executor.mapper.ExecutorInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorInstanceService {

    private final ExecutorInstanceMapper executorInstanceMapper;

    /**
     * 注册当前实例
     * @return 当前实例在在线列表中的槽位索引 (0-based)
     */
    public int register(String instanceId, int port) {
        ExecutorInstance exist = executorInstanceMapper.selectByInstanceId(instanceId);
        String host = getLocalHost();

        if (exist != null) {
            // 重启恢复：重新上线
            exist.setStatus("ONLINE");
            exist.setHost(host);
            exist.setPort(port);
            executorInstanceMapper.updateById(exist);
            executorInstanceMapper.updateHeartbeat(instanceId);
            log.info("Executor instance {} re-registered (host: {}, port: {})", instanceId, host, port);
        } else {
            ExecutorInstance inst = new ExecutorInstance();
            inst.setInstanceId(instanceId);
            inst.setHost(host);
            inst.setPort(port);
            inst.setStatus("ONLINE");
            inst.setTenantId(1L);
            executorInstanceMapper.insert(inst);
            log.info("Executor instance {} registered (host: {}, port: {})", instanceId, host, port);
        }

        // 返回槽位索引
        return getMySlotIndex(instanceId);
    }

    /** 发送心跳 */
    public void heartbeat(String instanceId) {
        executorInstanceMapper.updateHeartbeat(instanceId);
    }

    /** 标记死实例并返回死亡数量 */
    public int markDeadInstances() {
        int dead = executorInstanceMapper.markDeadInstances();
        if (dead > 0) {
            log.warn("Marked {} dead executor instances", dead);
        }
        return dead;
    }

    /** 获取当前实例的槽位索引 */
    public int getMySlotIndex(String instanceId) {
        List<ExecutorInstance> online = executorInstanceMapper.selectOnlineInstances();
        for (int i = 0; i < online.size(); i++) {
            if (online.get(i).getInstanceId().equals(instanceId)) {
                return i;
            }
        }
        return -1;
    }

    /** 获取在线实例总数 */
    public int getOnlineCount() {
        return executorInstanceMapper.countOnline();
    }

    /** 判断当前实例是否应该处理某个 job (槽位匹配) */
    public boolean shouldHandle(String instanceId, Long jobId) {
        int onlineCount = getOnlineCount();
        if (onlineCount == 0) return false;
        int mySlot = getMySlotIndex(instanceId);
        if (mySlot < 0) return false;
        return (int) (jobId % onlineCount) == mySlot;
    }

    /** 优雅下线 */
    public void shutdown(String instanceId) {
        ExecutorInstance inst = executorInstanceMapper.selectByInstanceId(instanceId);
        if (inst != null) {
            inst.setStatus("OFFLINE");
            executorInstanceMapper.updateById(inst);
            log.info("Executor instance {} shutdown gracefully", instanceId);
        }
    }

    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
