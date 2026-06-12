package com.cqie.datafactory.executor.engine;

import com.cqie.datafactory.common.context.TenantContext;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.cache.CacheManager;
import com.cqie.datafactory.executor.engine.cache.NodeHasher;
import com.cqie.datafactory.executor.engine.core.*;
import com.cqie.datafactory.executor.engine.core.model.*;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.cqie.datafactory.executor.engine.plugin.PluginContext;
import com.cqie.datafactory.executor.engine.plugin.PluginRegistry;
import com.cqie.datafactory.executor.util.RetryUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Component
public class ExecEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecEngine.class);
    private final DslParser dslParser = new DslParser();
    private final DslValidator dslValidator = new DslValidator();
    private final TopoSort topoSort = new TopoSort();
    private final ParamResolver paramResolver = new ParamResolver();
    private static final Set<String> NON_RETRYABLE_TYPES = Set.of("START", "END", "BRANCH", "FILTER");
    private static final Set<String> PLUGIN_TYPES = Set.of("DB", "API", "SCRIPT", "FILTER", "BRANCH", "COMMON_TASK", "START", "END");
    private static final Set<String> CATEGORY_TYPES = Set.of("数据接入", "数据处理", "流程控制");

    private final PluginRegistry pluginRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final NodeHasher nodeHasher;
    private final CacheManager cacheManager;
    private final ThreadPoolTaskExecutor taskExecutor;

    public ExecEngine(PluginRegistry pluginRegistry, JdbcTemplate jdbcTemplate,
                      NodeHasher nodeHasher, CacheManager cacheManager,
                      ThreadPoolTaskExecutor taskExecutor) {
        this.pluginRegistry = pluginRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.nodeHasher = nodeHasher;
        this.cacheManager = cacheManager;
        this.taskExecutor = taskExecutor;
    }

    public Map<String, Object> execute(String dslContent, String environment,
                                        Map<String, Object> triggerParams,
                                        Consumer<NodeExecutionRecord> nodeCallback) {
        log.info("ExecEngine.execute triggerParams: {}", triggerParams);
        DslModel dsl = dslParser.parse(dslContent);
        dslValidator.validate(dsl);
        List<List<NodeDef>> layers = topoSort.layeredSort(dsl);
        log.info("DAG layers: {} layers total", layers.size());

        Map<String, Map<String, Object>> nodeOutputsMap = new ConcurrentHashMap<>();
        Map<String, Object> resolvedVars = new ConcurrentHashMap<>();
        Map<String, Object> finalOutput = new ConcurrentHashMap<>();
        Map<String, Object> currentParams = triggerParams != null ? triggerParams : new HashMap<>();
        Set<String> skipSet = ConcurrentHashMap.newKeySet();
        Map<String, String> nodeHashMap = new ConcurrentHashMap<>();
        int totalLayers = layers.size();

        // Build outgoing edges index: nodeId -> list of edges
        Map<String, List<EdgeDef>> outgoingEdgesMap = new HashMap<>();
        for (EdgeDef e : dsl.getEdges()) {
            outgoingEdgesMap.computeIfAbsent(e.getSourceNodeId(), k -> new ArrayList<>()).add(e);
        }

        // Build reverse adjacency for skip propagation
        Map<String, List<String>> parentMap = new HashMap<>();
        for (EdgeDef e : dsl.getEdges()) {
            parentMap.computeIfAbsent(e.getTargetNodeId(), k -> new ArrayList<>()).add(e.getSourceNodeId());
        }

        // ---- Layer-based parallel execution ----
        for (int levelIdx = 0; levelIdx < layers.size(); levelIdx++) {
            List<NodeDef> layer = layers.get(levelIdx);
            log.info("[Layer {}/{}] Executing {} node(s): {}", levelIdx + 1, totalLayers,
                    layer.size(), layer.stream().map(NodeDef::getId).toList());

            int currentLayer = levelIdx + 1;
            if (layer.size() == 1) {
                // Single-node layer — execute directly, avoid thread overhead
                executeSingleNode(layer.get(0), nodeOutputsMap, parentMap, nodeHashMap,
                        skipSet, environment, outgoingEdgesMap, currentParams,
                        resolvedVars, finalOutput, dsl, nodeCallback, levelIdx, totalLayers);
            } else {
                // Multi-node layer — execute all nodes in parallel
                Long tenantId = TenantContext.get();
                List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                int levelIdxSnapshot = levelIdx;

                for (NodeDef nodeDef : layer) {
                    String nodeId = nodeDef.getId();
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        // Propagate tenant context to worker thread
                        if (tenantId != null) {
                            TenantContext.set(tenantId);
                        }
                        try {
                            executeSingleNode(nodeDef, nodeOutputsMap, parentMap, nodeHashMap,
                                    skipSet, environment, outgoingEdgesMap, currentParams,
                                    resolvedVars, finalOutput, dsl, nodeCallback, levelIdxSnapshot, totalLayers);
                        } catch (Exception e) {
                            exceptions.add(e);
                            log.error("[Layer {}/{}] Node {} failed: {}", currentLayer, totalLayers,
                                    nodeId, e.getMessage());
                        } finally {
                            TenantContext.clear();
                        }
                    }, taskExecutor);
                    futures.add(future);
                }

                // Wait for ALL nodes in this layer to finish before proceeding
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // If any node failed, propagate the first exception
                if (!exceptions.isEmpty()) {
                    log.error("[Layer {}/{}] {} node(s) failed in this layer, aborting pipeline",
                            levelIdx + 1, totalLayers, exceptions.size());
                    Throwable first = exceptions.get(0);
                    if (first instanceof BusinessException) {
                        throw (BusinessException) first;
                    }
                    throw new BusinessException("[Layer " + (levelIdx + 1) + "/" + totalLayers
                            + "] pipeline execution failed: " + first.getMessage());
                }
            }
        }

        return finalOutput;
    }

    /**
     * 执行单个节点（线程安全） — 包含 Branch 阻塞检查、缓存检查、重试执行、输出存储。
     * <p>
     * 在多节点层中会被多个线程并行调用，因此所有共享 Map 必须为线程安全实现。
     *
     * @param node             当前执行节点
     * @param nodeOutputsMap   节点输出映射（ConcurrentHashMap）
     * @param parentMap        反向邻接表（只读）
     * @param nodeHashMap      Merkle 哈希映射（ConcurrentHashMap）
     * @param skipSet          被 Branch 阻塞的节点集合
     * @param environment      运行环境
     * @param outgoingEdgesMap 出边索引（只读）
     * @param currentParams    START 节点的外部参数
     * @param resolvedVars     结果变量映射
     * @param finalOutput      最终输出累积映射
     * @param dsl              DSL 模型
     * @param nodeCallback     执行回调
     * @param levelIdx         当前层级索引（0-based）
     * @param totalLayers      总层数
     */
    private void executeSingleNode(NodeDef node,
                                   Map<String, Map<String, Object>> nodeOutputsMap,
                                   Map<String, List<String>> parentMap,
                                   Map<String, String> nodeHashMap,
                                   Set<String> skipSet,
                                   String environment,
                                   Map<String, List<EdgeDef>> outgoingEdgesMap,
                                   Map<String, Object> currentParams,
                                   Map<String, Object> resolvedVars,
                                   Map<String, Object> finalOutput,
                                   DslModel dsl,
                                   Consumer<NodeExecutionRecord> nodeCallback,
                                   int levelIdx,
                                   int totalLayers) {

        // 1. Branch 阻塞检查
        if (skipSet.contains(node.getId())) {
            NodeExecutionRecord record = new NodeExecutionRecord();
            record.nodeId = node.getId();
            record.nodeName = node.getDisplayName();
            record.nodeType = node.getType();
            record.componentCode = node.getComponentCode();
            record.status = "SKIPPED";
            record.startTime = System.currentTimeMillis();
            record.endTime = record.startTime;
            record.durationMs = 0;
            record.retryCount = 0;
            log.info("[Layer {}/{}] Node {} blocked by branch, skipping",
                    levelIdx + 1, totalLayers, node.getId());
            if (nodeCallback != null) {
                nodeCallback.accept(record);
            }
            return;
        }

        long startMs = System.currentTimeMillis();
        NodeExecutionRecord record = new NodeExecutionRecord();
        record.nodeId = node.getId();
        record.nodeName = node.getDisplayName();
        record.nodeType = node.getType();
        record.componentCode = node.getComponentCode();
        record.startTime = startMs;

        int maxRetries = readRetryCount(node);

        // ---- Incremental Caching ----
        // Compute upstream hashes for Merkle tree
        List<String> upstreamHashes = new ArrayList<>();
        List<String> parents = parentMap.getOrDefault(node.getId(), List.of());
        for (String parentId : parents) {
            String ph = nodeHashMap.get(parentId);
            if (ph != null) {
                upstreamHashes.add(ph);
            }
        }

        // Resolve field values from JsonNode to Map for hashing
        Map<String, Object> resolvedFieldValues = new HashMap<>();
        JsonNode fvNode = node.getFieldValues();
        if (fvNode != null && !fvNode.isNull()) {
            fvNode.fieldNames().forEachRemaining(field -> {
                JsonNode val = fvNode.get(field);
                if (val != null && !val.isNull()) {
                    resolvedFieldValues.put(field, val.isTextual() ? val.asText() : val.toString());
                }
            });
        }

        // Compute node hash
        String nodeHash = nodeHasher.computeHash(node, resolvedFieldValues, upstreamHashes);

        // Check cache
        Map<String, Object> cachedResult = cacheManager.lookup(nodeHash);
        if (cachedResult != null) {
            // Cache hit - use cached result, skip execution
            nodeOutputsMap.put(node.getId(), cachedResult);
            nodeHashMap.put(node.getId(), nodeHash);

            record.status = "SUCCESS";
            record.outputs = cachedResult;
            record.retryCount = 0;

            log.info("[Layer {}/{}] Node {} cache HIT, skipped execution (hash: {})",
                    levelIdx + 1, totalLayers, node.getId(),
                    nodeHash.length() > 12 ? nodeHash.substring(0, 12) : nodeHash);
        } else {
            // Cache miss - normal execution with retry loop
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                Map<String, Object> resolvedInputs = new HashMap<>();
                try {
                    String pluginType = resolvePluginType(node);

                    if ("START".equalsIgnoreCase(node.getType())) {
                        resolvedInputs.putAll(currentParams);
                    } else {
                        for (IoParamDef def : node.getInputParams()) {
                            String code = def.getParamCode();
                            resolvedInputs.put(code, paramResolver.resolve(def, nodeOutputsMap));
                        }
                    }

                    // END node: merge all upstream outputs
                    if ("END".equalsIgnoreCase(node.getType())) {
                        for (Map<String, Object> upstream : nodeOutputsMap.values()) {
                            resolvedInputs.putAll(upstream);
                        }
                    }

                    List<EdgeDef> outgoingEdges = outgoingEdgesMap.getOrDefault(node.getId(), List.of());
                    PluginContext ctx = new PluginContext(node, environment, resolvedInputs,
                            nodeOutputsMap, resolvedVars, outgoingEdges);
                    Map<String, Object> rawResult = pluginRegistry.get(pluginType).execute(ctx);

                    Map<String, Object> nodeOutputs = buildNodeOutputs(node, resolvedInputs, rawResult);
                    nodeOutputsMap.put(node.getId(), nodeOutputs);

                    // Store in cache
                    cacheManager.store(nodeHash, 0L, node.getId(), nodeOutputs,
                            System.currentTimeMillis() - startMs);
                    nodeHashMap.put(node.getId(), nodeHash);

                    // Branch routing: compute nodes to skip on inactive branches
                    if ("BRANCH".equalsIgnoreCase(pluginType)) {
                        Set<String> blocked = computeBlockedNodes(node, rawResult, dsl,
                                parentMap, outgoingEdgesMap);
                        skipSet.addAll(blocked);
                    }

                    String resultVar = readFieldValue(node.getFieldValues(), "result_var");
                    if (!resultVar.isBlank()) {
                        resolvedVars.put(resultVar, nodeOutputs);
                    }
                    if ("END".equalsIgnoreCase(node.getType())) {
                        finalOutput.putAll(nodeOutputs);
                    }

                    record.status = "SUCCESS";
                    record.outputs = rawResult;
                    record.retryCount = attempt - 1;
                    break;
                } catch (Exception e) {
                    record.retryCount = attempt;
                    if (attempt >= maxRetries || !RetryUtil.isRetryable(e)) {
                        record.status = "FAILURE";
                        record.errorMessage = e.getMessage();
                        if (nodeCallback != null) {
                            record.endTime = System.currentTimeMillis();
                            record.durationMs = record.endTime - startMs;
                            nodeCallback.accept(record);
                        }
                        throw new BusinessException("节点[" + node.getDisplayName()
                                + "]执行失败(已重试" + (attempt - 1) + "次): " + e.getMessage());
                    }
                    // 指数退避重试延迟
                    long delay = Math.min(1000L * (1L << (attempt - 1)), 30000L);
                    long jitter = ThreadLocalRandom.current().nextLong(501);
                    log.warn("[Layer {}/{}] Node {} retry {}/{} after {}ms",
                            levelIdx + 1, totalLayers, node.getId(),
                            attempt, maxRetries, delay + jitter);
                    try {
                        Thread.sleep(delay + jitter);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException("节点[" + node.getDisplayName()
                                + "]执行中断: " + ie.getMessage());
                    }
                }
            }
        }

        // Callback for successful / cache-hit execution
        record.endTime = System.currentTimeMillis();
        record.durationMs = record.endTime - startMs;
        if (nodeCallback != null) {
            nodeCallback.accept(record);
        }
    }

    /**
     * 阻塞传播算法：从 BRANCH 节点的非命中出边出发，找出所有被阻塞的下游节点。
     * 规则：一个节点被阻塞 ⟺ 它所有入边的来源节点都已被阻塞（没有任何活跃父节点）。
     */
    private Set<String> computeBlockedNodes(NodeDef branchNode, Map<String, Object> result,
                                            DslModel dsl,
                                            Map<String, List<String>> parentMap,
                                            Map<String, List<EdgeDef>> outgoingEdgesMap) {
        Set<String> blocked = new HashSet<>();
        String nextNodeId = result.get("nextNodeId") instanceof String s ? s : "";

        // Step 1: 收集 BRANCH 的非激活直连子节点
        List<EdgeDef> branchOut = outgoingEdgesMap.getOrDefault(branchNode.getId(), List.of());
        for (EdgeDef e : branchOut) {
            String target = e.getTargetNodeId();
            if (target != null && !target.equals(nextNodeId)) {
                blocked.add(target);
            }
        }

        if (blocked.isEmpty()) return blocked;

        // Step 2: 获取拓扑序中 BRANCH 之后的节点列表
        List<NodeDef> sequence = topoSort.sort(dsl);
        boolean foundBranch = false;

        // Step 3: 传播阻塞 —— 逐节点检查是否所有父节点都被阻塞
        for (NodeDef node : sequence) {
            if (node.getId().equals(branchNode.getId())) {
                foundBranch = true;
                continue;
            }
            if (!foundBranch) continue;

            List<String> parents = parentMap.getOrDefault(node.getId(), List.of());
            if (parents.isEmpty()) continue;

            boolean allParentsBlocked = true;
            for (String parent : parents) {
                if (!blocked.contains(parent)) {
                    allParentsBlocked = false;
                    break;
                }
            }
            if (allParentsBlocked) {
                blocked.add(node.getId());
            }
        }

        return blocked;
    }

    private Map<String, Object> buildNodeOutputs(NodeDef node, Map<String, Object> resolvedInputs,
                                                  Map<String, Object> rawResult) {
        Map<String, Object> outputs = new HashMap<>();
        // ScriptPlugin 将数据嵌套在 result 字段下，先尝试扁平化到顶层
        Map<String, Object> effectiveResult = rawResult;
        if (rawResult.containsKey("result") && rawResult.get("result") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inner = (Map<String, Object>) rawResult.get("result");
            effectiveResult = new HashMap<>(rawResult);
            effectiveResult.putAll(inner);  // 将 result 内的 rows/rowCount 等提升到顶层
        }
        for (IoParamDef def : node.getOutputParams()) {
            String code = def.getParamCode();
            if (effectiveResult.containsKey(code)) {
                outputs.put(code, effectiveResult.get(code));
            } else if (resolvedInputs.containsKey(code)) {
                outputs.put(code, resolvedInputs.get(code));
            }
        }
        if (outputs.isEmpty()) {
            outputs.putAll(effectiveResult);
        }
        return outputs;
    }

    /**
     * 根据节点类型和字段值推导实际执行的插件类型。
     * 如果 component_type 已经是插件直接支持的类型(DB/API/SCRIPT等)，直接返回；
     * 如果是中文分类(数据接入/数据处理/流程控制)，根据字段分析推导具体插件。
     */
    private String resolvePluginType(NodeDef node) {
        String type = node.getType() != null ? node.getType() : "";
        String upperType = type.toUpperCase();

        // START/END 是结构类型，共用 StartEndPlugin，END 也映射到 START
        if ("START".equalsIgnoreCase(type)) return "START";
        if ("END".equalsIgnoreCase(type)) return "START";

        // 已经是插件直接支持的类型
        if (pluginRegistry.has(upperType) || pluginRegistry.has(type)) {
            return type;
        }

        // 从字段值中推导
        JsonNode fv = node.getFieldValues();
        Set<String> fieldCodes = new HashSet<>();
        if (fv != null && !fv.isNull()) {
            fv.fieldNames().forEachRemaining(fieldCodes::add);
        }

        // 如果字段为空，尝试从数据库查询组件定义字段
        if (fieldCodes.isEmpty() && node.getComponentCode() != null && !node.getComponentCode().isBlank()
                && jdbcTemplate != null) {
            try {
                List<Map<String, Object>> dbFields = jdbcTemplate.queryForList(
                    "SELECT cf.field_code FROM component_field cf " +
                    "JOIN component c ON c.id = cf.component_id " +
                    "WHERE c.component_code = ?", node.getComponentCode());
                dbFields.forEach(row -> fieldCodes.add(Objects.toString(row.get("field_code"), "")));
            } catch (Exception ignore) {}
        }

        // 字段分析推导插件类型
        if (fieldCodes.contains("sql")) return "DB";
        if (fieldCodes.contains("url")) return "API";
        if (fieldCodes.contains("scriptCode")) return "SCRIPT";
        if (fieldCodes.contains("filterMode")) return "FILTER";
        if (fieldCodes.contains("subTaskId")) return "COMMON_TASK";
        if (fieldCodes.contains("branches")) return "BRANCH";

        // 根据组件名称/编码推导 START/END（不区分大小写）
        String name = node.getDisplayName() != null ? node.getDisplayName().toUpperCase() : "";
        String code = node.getComponentCode() != null ? node.getComponentCode().toUpperCase() : "";
        if (name.contains("START") || name.contains("开始") || code.contains("START")) return "START";
        if (name.contains("END") || name.contains("结束") || code.contains("END")) return "START";

        // 按分类兜底
        return switch (type) {
            case "数据接入" -> "DB";
            case "数据处理" -> "SCRIPT";
            case "流程控制" -> "START";
            default -> throw new BusinessException("无法识别组件执行类型: " + type);
        };
    }

    private int readRetryCount(NodeDef node) {
        String pluginType = resolvePluginType(node).toUpperCase();
        if (NON_RETRYABLE_TYPES.contains(pluginType)) {
            return 1;
        }
        try {
            if (node.getFieldValues() != null && !node.getFieldValues().isNull()) {
                var retryNode = node.getFieldValues().get("retryCount");
                if (retryNode != null && !retryNode.isNull()) {
                    return Math.max(1, Integer.parseInt(retryNode.asText()));
                }
            }
        } catch (Exception ignore) {}
        return 1;
    }

    public static class NodeExecutionRecord {
        public String nodeId;
        public String nodeName;
        public String nodeType;
        public String componentCode;
        public String status;
        public long startTime;
        public long endTime;
        public long durationMs;
        public int retryCount;
        public Map<String, Object> outputs;
        public String errorMessage;
    }

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) return val.asText();
        }
        return "";
    }
}
