package com.cqie.datafactory.executor.engine.cache;

import com.cqie.datafactory.executor.entity.NodeCache;
import com.cqie.datafactory.executor.mapper.NodeCacheMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DAG节点缓存管理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheManager {

    private final NodeCacheMapper nodeCacheMapper;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 查询缓存
     * @return 缓存命中时返回结果, 未命中返回 null
     */
    public Map<String, Object> lookup(String nodeHash) {
        NodeCache cache = nodeCacheMapper.selectByHash(nodeHash);
        if (cache == null) {
            return null;
        }
        try {
            log.debug("Cache HIT for hash: {}", nodeHash.substring(0, 12));
            return MAPPER.readValue(cache.getResultJson(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Cache deserialization failed for hash: {}", nodeHash.substring(0, 12));
            return null;
        }
    }

    /**
     * 存储缓存
     */
    public void store(String nodeHash, Long taskId, String nodeId,
                       Map<String, Object> result, long costMs) {
        try {
            NodeCache cache = new NodeCache();
            cache.setNodeHash(nodeHash);
            cache.setTaskId(taskId);
            cache.setNodeId(nodeId);
            cache.setResultJson(MAPPER.writeValueAsString(result));
            cache.setCostMs(costMs);
            nodeCacheMapper.insert(cache);
            log.debug("Cache stored for hash: {}", nodeHash.substring(0, 12));
        } catch (Exception e) {
            log.warn("Failed to store cache for node {}: {}", nodeId, e.getMessage());
        }
    }

    /** 清理过期缓存 */
    public int cleanExpired(int retentionDays) {
        return nodeCacheMapper.deleteOlderThan(retentionDays);
    }
}
