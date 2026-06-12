package com.cqie.datafactory.executor.engine.cache;

import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 计算DAG节点的内容寻址哈希
 * hash = SHA256(nodeType + sorted(fieldValues) + sorted(upstream_hashes))
 */
@Slf4j
@Component
public class NodeHasher {

    /** 计算节点哈希 */
    public String computeHash(NodeDef nodeDef, Map<String, Object> fieldValues,
                               List<String> upstreamHashes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            // 1. 节点类型
            md.update(nodeDef.getType().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);

            // 2. 字段值 (排序保证确定性)
            TreeMap<String, Object> sorted = new TreeMap<>(fieldValues);
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                md.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '=');
                md.update(String.valueOf(entry.getValue()).getBytes(StandardCharsets.UTF_8));
                md.update((byte) '|');
            }
            md.update((byte) 0);

            // 3. 上游哈希 (Merkle树 — 上游变更自动级联失效)
            if (upstreamHashes != null) {
                for (String hash : upstreamHashes.stream().sorted().toList()) {
                    md.update(hash.getBytes(StandardCharsets.UTF_8));
                    md.update((byte) ',');
                }
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
