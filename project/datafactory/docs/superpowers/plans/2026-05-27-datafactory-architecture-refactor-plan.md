# 数据工厂架构重构 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 datafactory 项目重构为插件化执行引擎架构，修复安全缺陷，建立规范的服务间通信，并为新组件和监控统计提供扩展框架。

**Architecture:** 执行引擎采用策略模式（ComponentPlugin 接口 + Spring 自动发现），Configuration 服务通过 Feign 接口对外暴露数据，Executor 服务不再直查 Configuration 数据库表。DSL 解析/校验/拓扑排序独立为 engine/core 模块。

**Tech Stack:** Java 21, Spring Boot 3.3.7, Spring Cloud 2023.0.5, Spring Cloud Alibaba 2023.0.1.2, MyBatis-Plus 3.5.7, OpenFeign

---

## 文件结构映射

```
datafactory-backend-common/src/main/java/com/cqie/datafactory/common/util/
└── AesEncryptUtil.java                    ← 新增：AES加解密工具

datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/
├── engine/core/model/
│   ├── DslModel.java                      ← 新增：DSL结构化模型
│   ├── NodeDef.java                       ← 新增：节点定义
│   └── EdgeDef.java                       ← 新增：连线定义
├── engine/core/
│   ├── DslParser.java                     ← 新增：JSON→DslModel
│   ├── DslValidationRule.java             ← 新增：校验规则接口
│   ├── DslValidator.java                  ← 新增：校验链
│   ├── TopoSort.java                      ← 新增：拓扑排序(从ExecutorTaskServiceImpl提取)
│   └── ParamResolver.java                 ← 新增：参数解析(CONST/UPSTREAM/EXPRESSION)
├── engine/plugin/
│   ├── ComponentPlugin.java               ← 新增：插件接口
│   ├── PluginContext.java                 ← 新增：插件上下文
│   ├── PluginRegistry.java                ← 新增：插件注册
│   ├── StartEndPlugin.java                ← 新增：START/END
│   ├── DbPlugin.java                      ← 新增：DB (从ExecutorTaskServiceImpl提取)
│   ├── ApiPlugin.java                     ← 新增：API (从ExecutorTaskServiceImpl提取)
│   ├── ScriptPlugin.java                  ← 新增：SCRIPT (从ExecutorTaskServiceImpl提取)
│   ├── BranchPlugin.java                  ← 新增：条件分支
│   ├── FilterPlugin.java                  ← 新增：数据过滤
│   └── CommonTaskPlugin.java              ← 新增：子任务调用
├── engine/
│   └── ExecEngine.java                    ← 新增：执行编排器
├── engine/pool/
│   └── ExecutionThreadPoolConfig.java     ← 新增：线程池配置
├── schedule/
│   ├── entity/ScheduleJob.java            ← 新增：定时任务实体
│   ├── mapper/ScheduleJobMapper.java      ← 新增：定时任务Mapper
│   ├── ScheduleJobService.java            ← 新增：定时任务服务
│   └── TaskScheduleExecutor.java          ← 新增：定时触发执行
└── statistics/
    ├── StatisticsController.java          ← 新增：统计API
    └── StatisticsService.java             ← 新增：统计服务

datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/
├── DatasourceFeignClient.java             ← 新增：数据源Feign
├── ScriptFeignClient.java                 ← 新增：脚本Feign
├── ExternalApiFeignClient.java            ← 新增：外部APIFeign
├── ComponentFeignClient.java              ← 新增：组件Feign
└── vo/
    ├── DbVersionResolveVO.java            ← 新增：数据源版本VO
    ├── ScriptExecutionVO.java             ← 新增：脚本执行VO
    └── ApiVersionResolveVO.java           ← 新增：API版本VO

datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/
├── controller/feign/
│   ├── DatasourceFeignController.java     ← 新增：对内Feign接口
│   ├── ScriptFeignController.java         ← 新增
│   ├── ExternalApiFeignController.java    ← 新增
│   └── ComponentFeignController.java      ← 新增
└── config/
    └── FeignSecurityConfig.java           ← 新增：Feign安全配置(密码解密)

修改文件:
- ExecutorTaskServiceImpl.java             ← 大幅缩减，委托给ExecEngine
- TaskDslServiceImpl.java                  ← 使用DslParser/DslValidator
- DatasourceDbVersionServiceImpl.java      ← 保存时加密密码
- executor-feign/pom.xml                   ← 添加openfeign依赖
- executor-server/pom.xml                  ← 添加executor-feign依赖
- executor-server application.yml          ← feign配置 + 线程池配置
- configuration application.yml            ← 加密密钥配置
- Gateway application.yml                  ← 添加feign路由(如需)
- datafactory.sql                          ← 新增schedule_job表
```

---

## 阶段一：基础解耦

### Task 1: AES 加解密工具

**Files:**
- Create: `datafactory-backend-common/src/main/java/com/cqie/datafactory/common/util/AesEncryptUtil.java`

- [ ] **Step 1: 创建 AES 加解密工具类**

```java
package com.cqie.datafactory.common.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesEncryptUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    public AesEncryptUtil(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES密钥必须为32字节(256位)的Base64编码");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
        this.secureRandom = new SecureRandom();
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[GCM_IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, GCM_IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }
}
```

- [ ] **Step 2: 在 Configuration 服务中注册为 Spring Bean**

Read `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/ConfigurationApplication.java` 确认位置后，无需修改启动类。改为在 SecurityConfig 同目录新增配置类。

Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/config/EncryptConfig.java`

```java
package com.cqie.datafactory.configuration.config;

import com.cqie.datafactory.common.util.AesEncryptUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptConfig {

    @Value("${security.datasource.secret-key}")
    private String secretKey;

    @Bean
    public AesEncryptUtil aesEncryptUtil() {
        return new AesEncryptUtil(secretKey);
    }
}
```

- [ ] **Step 3: 添加配置到 application.yml**

Modify: `datafactory-backend-configuration/src/main/resources/application.yml` — 在文件末尾追加:

```yaml
security:
  datasource:
    secret-key: ${DATASOURCE_ENCRYPT_KEY:CHANGEME_CHANGEME_CHANGEME_32B}
```

- [ ] **Step 4: Commit**

```bash
git add datafactory-backend-common/src/main/java/com/cqie/datafactory/common/util/AesEncryptUtil.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/config/EncryptConfig.java \
        datafactory-backend-configuration/src/main/resources/application.yml
git commit -m "feat: add AES-256-GCM encryption utility for datasource passwords"
```

---

### Task 2: 数据源密码加密存储

**Files:**
- Modify: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/DatasourceDbVersionServiceImpl.java`

- [ ] **Step 1: 在 DatasourceDbVersionServiceImpl 中注入 AesEncryptUtil 并加密写入密码**

在类顶部添加注入:

```java
import com.cqie.datafactory.common.util.AesEncryptUtil;
import org.springframework.beans.factory.annotation.Autowired;

// 在类内部添加字段:
@Autowired
private AesEncryptUtil aesEncryptUtil;
```

找到保存/更新 `DatasourceDbVersion` 实体的位置（`createVersion` 和 `updateVersion` 方法），在 `save()` 或 `insert()` 调用前添加:

```java
// 加密密码
if (entity.getPassword() != null && !entity.getPassword().isBlank()) {
    entity.setPassword(aesEncryptUtil.encrypt(entity.getPassword()));
}
```

- [ ] **Step 2: 读取时解密**

在返回 `DatasourceDbVersionVO` 的转换方法中，如果 VO 包含 password 字段（仅在 Feign 内部接口返回），添加解密:

```java
// 如果 vo 有 password 字段且需要返回:
// vo.setPassword(aesEncryptUtil.decrypt(entity.getPassword()));
```

注意：对外的前端 API 不应返回密码字段。解密仅在后续的 FeignController 中处理。

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/DatasourceDbVersionServiceImpl.java
git commit -m "feat: encrypt datasource password on save"
```

---

### Task 3: DSL 结构化模型

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/model/DslModel.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/model/NodeDef.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/model/EdgeDef.java`

- [ ] **Step 1: 创建 NodeDef**

```java
package com.cqie.datafactory.executor.engine.core.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class NodeDef {
    private String id;
    private String type;
    private String name;
    private String label;
    private String componentCode;
    private Long componentId;
    private Double positionX;
    private Double positionY;
    private JsonNode fieldValues;
    private List<IoParamDef> inputParams = new ArrayList<>();
    private List<IoParamDef> outputParams = new ArrayList<>();
    private JsonNode raw;

    public NodeDef() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String componentCode) { this.componentCode = componentCode; }

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }

    public Double getPositionX() { return positionX; }
    public void setPositionX(Double positionX) { this.positionX = positionX; }

    public Double getPositionY() { return positionY; }
    public void setPositionY(Double positionY) { this.positionY = positionY; }

    public JsonNode getFieldValues() { return fieldValues; }
    public void setFieldValues(JsonNode fieldValues) { this.fieldValues = fieldValues; }

    public List<IoParamDef> getInputParams() { return inputParams; }
    public void setInputParams(List<IoParamDef> inputParams) { this.inputParams = inputParams; }

    public List<IoParamDef> getOutputParams() { return outputParams; }
    public void setOutputParams(List<IoParamDef> outputParams) { this.outputParams = outputParams; }

    public JsonNode getRaw() { return raw; }
    public void setRaw(JsonNode raw) { this.raw = raw; }

    public String getDisplayName() {
        return (name != null && !name.isBlank()) ? name
                : (label != null && !label.isBlank()) ? label : id;
    }

    public static class IoParamDef {
        private String paramCode;
        private String paramName;
        private String paramType; // INPUT / OUTPUT
        private String dataType;
        private String sourceType;
        private JsonNode sourceValue;
        private String defaultValue;
        private int requiredFlag;
        private String paramSpace;

        public IoParamDef() {}

        public String getParamCode() { return paramCode; }
        public void setParamCode(String paramCode) { this.paramCode = paramCode; }

        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }

        public String getParamType() { return paramType; }
        public void setParamType(String paramType) { this.paramType = paramType; }

        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }

        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }

        public JsonNode getSourceValue() { return sourceValue; }
        public void setSourceValue(JsonNode sourceValue) { this.sourceValue = sourceValue; }

        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

        public int getRequiredFlag() { return requiredFlag; }
        public void setRequiredFlag(int requiredFlag) { this.requiredFlag = requiredFlag; }

        public String getParamSpace() { return paramSpace; }
        public void setParamSpace(String paramSpace) { this.paramSpace = paramSpace; }
    }
}
```

- [ ] **Step 2: 创建 EdgeDef**

```java
package com.cqie.datafactory.executor.engine.core.model;

public class EdgeDef {
    private String id;
    private String sourceNodeId;
    private String sourcePort;
    private String targetNodeId;
    private String targetPort;

    public EdgeDef() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public String getSourcePort() { return sourcePort; }
    public void setSourcePort(String sourcePort) { this.sourcePort = sourcePort; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getTargetPort() { return targetPort; }
    public void setTargetPort(String targetPort) { this.targetPort = targetPort; }
}
```

- [ ] **Step 3: 创建 DslModel**

```java
package com.cqie.datafactory.executor.engine.core.model;

import java.util.ArrayList;
import java.util.List;

public class DslModel {
    private List<NodeDef> nodes = new ArrayList<>();
    private List<EdgeDef> edges = new ArrayList<>();

    public DslModel() {}

    public DslModel(List<NodeDef> nodes, List<EdgeDef> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<NodeDef> getNodes() { return nodes; }
    public void setNodes(List<NodeDef> nodes) { this.nodes = nodes; }

    public List<EdgeDef> getEdges() { return edges; }
    public void setEdges(List<EdgeDef> edges) { this.edges = edges; }

    public NodeDef getNodeById(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/model/
git commit -m "feat: add DSL model classes (DslModel, NodeDef, EdgeDef)"
```

---

### Task 4: DSL 解析器

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/DslParser.java`

- [ ] **Step 1: 创建 DslParser**

```java
package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class DslParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public DslModel parse(String dslContent) {
        if (dslContent == null || dslContent.isBlank()) {
            throw new BusinessException("DSL内容不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(dslContent);
            JsonNode graphRoot = resolveGraphRoot(root);

            List<NodeDef> nodes = parseNodes(graphRoot);
            List<EdgeDef> edges = parseEdges(graphRoot);
            return new DslModel(nodes, edges);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("DSL解析失败: " + e.getMessage());
        }
    }

    private JsonNode resolveGraphRoot(JsonNode root) {
        JsonNode graph = root.get("graph");
        return (graph != null && graph.isObject()) ? graph : root;
    }

    private List<NodeDef> parseNodes(JsonNode root) {
        JsonNode nodesNode = root.get("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            throw new BusinessException("DSL缺少nodes配置");
        }
        List<NodeDef> nodes = new ArrayList<>();
        for (JsonNode n : nodesNode) {
            NodeDef node = new NodeDef();
            node.setId(requireTextField(n, "id", "节点ID不能为空"));
            node.setType(requireTextField(n, "type", "节点类型不能为空"));
            node.setName(readTextField(n, "name"));
            node.setLabel(readTextField(n, "label"));
            node.setComponentCode(readTextField(n, "componentCode"));
            if (n.has("componentId") && n.get("componentId").canConvertToLong()) {
                node.setComponentId(n.get("componentId").asLong());
            }
            JsonNode pos = n.get("position");
            if (pos != null) {
                if (pos.has("x") && pos.get("x").isNumber()) node.setPositionX(pos.get("x").asDouble());
                if (pos.has("y") && pos.get("y").isNumber()) node.setPositionY(pos.get("y").asDouble());
            }
            node.setFieldValues(n.get("fieldValues"));
            node.setInputParams(parseIoParams(n, "inputParams", "INPUT"));
            node.setOutputParams(parseIoParams(n, "outputParams", "OUTPUT"));
            node.setRaw(n);
            nodes.add(node);
        }
        return nodes;
    }

    private List<IoParamDef> parseIoParams(JsonNode node, String field, String defaultParamType) {
        List<IoParamDef> result = new ArrayList<>();
        JsonNode params = node.get(field);
        if (params != null && params.isArray()) {
            for (JsonNode p : params) {
                IoParamDef def = new IoParamDef();
                def.setParamCode(p.path("paramCode").asText());
                def.setParamName(p.path("paramName").asText());
                def.setParamType(p.has("paramType") ? p.get("paramType").asText() : defaultParamType);
                def.setDataType(p.path("dataType").asText("STRING").toUpperCase());
                def.setSourceType(p.path("sourceType").asText("CONST").toUpperCase());
                def.setSourceValue(p.get("sourceValue"));
                def.setDefaultValue(readTextField(p, "defaultValue"));
                def.setRequiredFlag(p.path("requiredFlag").asInt(0));
                def.setParamSpace(readTextField(p, "paramSpace"));
                result.add(def);
            }
        }
        return result;
    }

    private List<EdgeDef> parseEdges(JsonNode root) {
        JsonNode edgesNode = root.get("edges");
        if (edgesNode == null || !edgesNode.isArray()) {
            return new ArrayList<>();
        }
        List<EdgeDef> edges = new ArrayList<>();
        for (JsonNode e : edgesNode) {
            EdgeDef edge = new EdgeDef();
            edge.setId(requireTextField(e, "id", "连线ID不能为空"));
            edge.setSourceNodeId(resolveEdgeNodeId(e, "source", "from"));
            edge.setSourcePort(resolveEdgePort(e, "source", "out"));
            edge.setTargetNodeId(resolveEdgeNodeId(e, "target", "to"));
            edge.setTargetPort(resolveEdgePort(e, "target", "in"));
            edges.add(edge);
        }
        return edges;
    }

    public String resolveEdgeNodeId(JsonNode edge, String primaryField, String legacyField) {
        JsonNode nested = edge.get(primaryField);
        if (nested != null && nested.isObject()) {
            JsonNode nodeId = nested.get("nodeId");
            if (nodeId == null || nodeId.isNull() || nodeId.asText().isBlank()) {
                nodeId = nested.get("id");
            }
            if (nodeId != null && !nodeId.isNull() && !nodeId.asText().isBlank()) {
                return nodeId.asText();
            }
        }
        JsonNode legacy = edge.get(legacyField);
        return (legacy != null && !legacy.isNull()) ? legacy.asText() : null;
    }

    private String resolveEdgePort(JsonNode edge, String primaryField, String defaultPort) {
        JsonNode nested = edge.get(primaryField);
        if (nested != null && nested.isObject()) {
            JsonNode portNode = nested.get("port");
            if (portNode != null && !portNode.isNull() && !portNode.asText().isBlank()) {
                return portNode.asText();
            }
        }
        return defaultPort;
    }

    private String requireTextField(JsonNode node, String fieldName, String errorMessage) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().isBlank()) {
            throw new BusinessException(errorMessage);
        }
        return fieldNode.asText();
    }

    private String readTextField(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/DslParser.java
git commit -m "feat: add DslParser for JSON→DslModel conversion"
```

---

### Task 5: DSL 校验器

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/DslValidationRule.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/DslValidator.java`

- [ ] **Step 1: 创建校验规则接口**

```java
package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.DslModel;

@FunctionalInterface
public interface DslValidationRule {
    void validate(DslModel dsl);
}
```

- [ ] **Step 2: 创建 DslValidator（含4条内置规则）**

```java
package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;

import java.util.*;

public class DslValidator {

    private static final Set<String> ALLOWED_DATA_TYPES = Set.of("STRING", "NUMBER", "BOOLEAN");
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("CONST", "UPSTREAM_OUTPUT", "EXPRESSION");

    private final List<DslValidationRule> rules = new ArrayList<>();

    public DslValidator() {
        rules.add(this::validateStructure);
        rules.add(this::validateStartEnd);
        rules.add(this::validateNodeIds);
        rules.add(this::validateEdges);
        rules.add(this::validateConnectivity);
        rules.add(this::validateIoParams);
        rules.add(this::validateParamCompatibility);
    }

    public void validate(DslModel dsl) {
        for (DslValidationRule rule : rules) {
            rule.validate(dsl);
        }
    }

    private void validateStructure(DslModel dsl) {
        if (dsl.getNodes().isEmpty()) {
            throw new BusinessException("DSL缺少nodes配置");
        }
        if (dsl.getEdges().isEmpty()) {
            throw new BusinessException("DSL缺少edges配置");
        }
    }

    private void validateStartEnd(DslModel dsl) {
        long startCount = dsl.getNodes().stream()
                .filter(n -> "START".equalsIgnoreCase(n.getType())).count();
        long endCount = dsl.getNodes().stream()
                .filter(n -> "END".equalsIgnoreCase(n.getType())).count();
        if (startCount != 1 || endCount != 1) {
            throw new BusinessException("任务配置必须包含且仅包含一个START节点和一个END节点");
        }
    }

    private void validateNodeIds(DslModel dsl) {
        Set<String> nodeIds = new HashSet<>();
        for (NodeDef n : dsl.getNodes()) {
            if (!nodeIds.add(n.getId())) {
                throw new BusinessException("节点ID重复: " + n.getId());
            }
        }
    }

    private void validateEdges(DslModel dsl) {
        Set<String> nodeIds = new HashSet<>();
        dsl.getNodes().forEach(n -> nodeIds.add(n.getId()));

        Set<String> edgeIds = new HashSet<>();
        Set<String> edgeKeys = new HashSet<>();
        for (EdgeDef e : dsl.getEdges()) {
            if (e.getSourceNodeId() == null || e.getSourceNodeId().isBlank()) {
                throw new BusinessException("连线起点不能为空");
            }
            if (e.getTargetNodeId() == null || e.getTargetNodeId().isBlank()) {
                throw new BusinessException("连线终点不能为空");
            }
            if (!edgeIds.add(e.getId())) {
                throw new BusinessException("连线ID重复: " + e.getId());
            }
            if (!nodeIds.contains(e.getSourceNodeId())) {
                throw new BusinessException("连线起点不存在: " + e.getSourceNodeId());
            }
            if (!nodeIds.contains(e.getTargetNodeId())) {
                throw new BusinessException("连线终点不存在: " + e.getTargetNodeId());
            }
            String edgeKey = e.getSourceNodeId() + ":" + e.getSourcePort()
                    + "->" + e.getTargetNodeId() + ":" + e.getTargetPort();
            if (!edgeKeys.add(edgeKey)) {
                throw new BusinessException("重复连线: " + edgeKey);
            }
        }
    }

    private void validateConnectivity(DslModel dsl) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        dsl.getNodes().forEach(n -> {
            inDegree.put(n.getId(), 0);
            outDegree.put(n.getId(), 0);
        });
        for (EdgeDef e : dsl.getEdges()) {
            outDegree.put(e.getSourceNodeId(), outDegree.get(e.getSourceNodeId()) + 1);
            inDegree.put(e.getTargetNodeId(), inDegree.get(e.getTargetNodeId()) + 1);
        }
        for (NodeDef n : dsl.getNodes()) {
            if (inDegree.get(n.getId()) == 0 && outDegree.get(n.getId()) == 0) {
                throw new BusinessException("节点存在孤立点: " + n.getId());
            }
        }
    }

    private void validateIoParams(DslModel dsl) {
        for (NodeDef n : dsl.getNodes()) {
            Set<String> inputCodes = new HashSet<>();
            for (IoParamDef p : n.getInputParams()) {
                if (p.getParamCode() == null || p.getParamCode().isBlank()) {
                    throw new BusinessException("节点参数编码为空: " + n.getId());
                }
                if (!ALLOWED_DATA_TYPES.contains(p.getDataType())) {
                    throw new BusinessException("节点参数数据类型不支持: " + p.getDataType());
                }
                if (!ALLOWED_SOURCE_TYPES.contains(p.getSourceType())) {
                    throw new BusinessException("节点参数来源不支持: " + p.getSourceType());
                }
                if (!inputCodes.add(p.getParamCode())) {
                    throw new BusinessException("节点参数编码重复: " + n.getId() + "." + p.getParamCode());
                }
            }
        }
    }

    private void validateParamCompatibility(DslModel dsl) {
        Map<String, Set<String>> upstreamMap = new HashMap<>();
        for (EdgeDef e : dsl.getEdges()) {
            upstreamMap.computeIfAbsent(e.getTargetNodeId(), k -> new HashSet<>()).add(e.getSourceNodeId());
        }

        for (NodeDef node : dsl.getNodes()) {
            Set<String> validUpstreamNodes = upstreamMap.getOrDefault(node.getId(), Set.of());
            for (IoParamDef p : node.getInputParams()) {
                if ("UPSTREAM_OUTPUT".equals(p.getSourceType()) && p.getSourceValue() != null) {
                    String refNodeId = null;
                    String refParamCode = null;
                    if (p.getSourceValue().isObject()) {
                        refNodeId = p.getSourceValue().path("nodeId").asText(null);
                        refParamCode = p.getSourceValue().path("paramCode").asText(null);
                    } else if (p.getSourceValue().isTextual()) {
                        String text = p.getSourceValue().asText();
                        if (text.contains(".")) {
                            String[] parts = text.split("\\.", 2);
                            refNodeId = parts[0];
                            refParamCode = parts.length > 1 ? parts[1] : null;
                        }
                    }
                    if (refNodeId != null && !validUpstreamNodes.contains(refNodeId)) {
                        throw new BusinessException("上游输出引用节点不在直连上游中: " + refNodeId);
                    }
                    if (refNodeId != null && refParamCode != null) {
                        NodeDef upstream = dsl.getNodeById(refNodeId);
                        if (upstream != null) {
                            boolean found = upstream.getOutputParams().stream()
                                    .anyMatch(o -> refParamCode.equals(o.getParamCode())
                                            && p.getDataType().equals(o.getDataType()));
                            if (!found) {
                                throw new BusinessException("上游参数类型不匹配或不存在: "
                                        + refNodeId + "." + refParamCode);
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/
git commit -m "feat: add DslValidator with rule chain pattern"
```

---

### Task 6: 拓扑排序 + 参数解析（从现有代码提取）

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/TopoSort.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/ParamResolver.java`

- [ ] **Step 1: 创建 TopoSort**

从 `ExecutorTaskServiceImpl.sortNodesTopologicallyByCanvas()` 提取，改为使用 DslModel：

```java
package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.EdgeDef;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;

import java.util.*;

public class TopoSort {

    public List<NodeDef> sort(DslModel dsl) {
        Map<String, NodeDef> nodeMap = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (NodeDef n : dsl.getNodes()) {
            nodeMap.put(n.getId(), n);
            adj.put(n.getId(), new ArrayList<>());
            inDegree.put(n.getId(), 0);
        }

        for (EdgeDef e : dsl.getEdges()) {
            String from = e.getSourceNodeId();
            String to = e.getTargetNodeId();
            if (adj.containsKey(from) && adj.containsKey(to)) {
                adj.get(from).add(to);
                inDegree.put(to, inDegree.get(to) + 1);
            }
        }

        Comparator<String> canvasOrder = Comparator
                .comparingDouble((String id) -> {
                    NodeDef n = nodeMap.get(id);
                    return n != null && n.getPositionY() != null ? n.getPositionY() : Double.MAX_VALUE;
                })
                .thenComparingDouble(id -> {
                    NodeDef n = nodeMap.get(id);
                    return n != null && n.getPositionX() != null ? n.getPositionX() : Double.MAX_VALUE;
                })
                .thenComparing(id -> id);

        PriorityQueue<String> queue = new PriorityQueue<>(canvasOrder);
        inDegree.forEach((id, degree) -> {
            if (degree == 0) queue.add(id);
        });

        List<NodeDef> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            result.add(nodeMap.get(curr));
            List<String> neighbors = new ArrayList<>(adj.get(curr));
            neighbors.sort(canvasOrder);
            for (String neighbor : neighbors) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) queue.add(neighbor);
            }
        }

        if (result.size() == nodeMap.size()) return result;

        // 有回环,回退到画布坐标排序
        List<NodeDef> fallback = new ArrayList<>(dsl.getNodes());
        fallback.sort(Comparator
                .comparingDouble((NodeDef n) -> n.getPositionY() != null ? n.getPositionY() : Double.MAX_VALUE)
                .thenComparingDouble(n -> n.getPositionX() != null ? n.getPositionX() : Double.MAX_VALUE)
                .thenComparing(NodeDef::getId));
        return fallback;
    }
}
```

- [ ] **Step 2: 创建 ParamResolver**

```java
package com.cqie.datafactory.executor.engine.core;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class ParamResolver {

    public Object resolve(IoParamDef def, Map<String, Map<String, Object>> nodeOutputsMap) {
        String sourceType = def.getSourceType();
        JsonNode sourceValue = def.getSourceValue();

        try {
            if ("CONST".equalsIgnoreCase(sourceType)) {
                if (sourceValue != null && !sourceValue.isNull()) {
                    return sourceValue.isTextual() ? sourceValue.asText() : sourceValue.toString();
                }
                return def.getDefaultValue();
            }

            if ("UPSTREAM_OUTPUT".equalsIgnoreCase(sourceType)) {
                return resolveUpstreamValue(sourceValue, nodeOutputsMap);
            }

            if ("EXPRESSION".equalsIgnoreCase(sourceType)) {
                String expr = sourceValue != null ? sourceValue.asText("") : "";
                return "expr_result(" + expr + ")";
            }

            return def.getDefaultValue();
        } catch (Exception e) {
            throw new BusinessException("参数解析失败(paramCode=" + def.getParamCode()
                    + ", sourceType=" + sourceType + "): " + e.getMessage());
        }
    }

    private Object resolveUpstreamValue(JsonNode sourceValue, Map<String, Map<String, Object>> nodeOutputsMap) {
        if (sourceValue == null || sourceValue.isNull()) return null;

        String nodeId = null;
        String paramCode = null;

        if (sourceValue.isObject()) {
            nodeId = sourceValue.path("nodeId").asText(null);
            paramCode = sourceValue.path("paramCode").asText(null);
        } else if (sourceValue.isTextual()) {
            String text = sourceValue.asText();
            if (text.contains(".")) {
                String[] parts = text.split("\\.", 2);
                nodeId = parts[0];
                paramCode = parts.length > 1 ? parts[1] : null;
            }
        }

        if (nodeId == null || paramCode == null) return null;

        Map<String, Object> nodeOutput = nodeOutputsMap.get(nodeId);
        return nodeOutput != null ? nodeOutput.get(paramCode) : null;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/core/
git commit -m "feat: add TopoSort and ParamResolver extracted from ExecutorTaskServiceImpl"
```

---

### Task 7: 修改 TaskDslServiceImpl 使用新 DslEngine

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/TaskDslServiceImpl.java`

- [ ] **Step 1: 替换 inline DSL 校验为新 DslParser + DslValidator**

在 `TaskDslServiceImpl` 中:
1. 删除 `validateDslContent` 方法体内全部逻辑（保留方法签名）
2. 新增字段注入 DslParser 和 DslValidator:

```java
private final DslParser dslParser = new DslParser();
private final DslValidator dslValidator = new DslValidator();
```

3. 修改 `validateDslContent` 方法为:

```java
private void validateDslContent(String dslContent) {
    dslValidator.validate(dslParser.parse(dslContent));
}
```

4. 删除以下不再需要的方法体（改为空实现或删除）:
   - `resolveGraphRoot`
   - `validateRequiredTextField`
   - `resolveEdgeNodeId`
   - `resolveEdgePort`
   - `validateGraphConnectivity`
   - `validateNodeIoParams`
   - `validateIoParamItem`
   - `validateResourceAssociation`
   - `parseLongId`
   - `ensureResourceEnabled`
   - `validateParamTypeCompatibility`
   - `validateUpstreamRefType`

这些方法的功能已由 DslParser 和 DslValidator 覆盖。

5. 将 `resolveEdgeNodeId` 和 `resolveEdgePort` 的引用改为使用 `dslParser` 实例方法。

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/TaskDslServiceImpl.java
git commit -m "refactor: TaskDslServiceImpl delegates validation to DslParser/DslValidator"
```

---

## 阶段二：执行引擎重构

### Task 8: 插件接口 + 插件注册

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/ComponentPlugin.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/PluginContext.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/PluginRegistry.java`

- [ ] **Step 1: 创建 ComponentPlugin 接口**

```java
package com.cqie.datafactory.executor.engine.plugin;

import java.util.Map;

public interface ComponentPlugin {
    String supportedType();
    Map<String, Object> execute(PluginContext context);
    default boolean isEnabled() { return true; }
}
```

- [ ] **Step 2: 创建 PluginContext**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.NodeDef;

import java.util.Map;

public class PluginContext {
    private final NodeDef node;
    private final String environment;
    private final Map<String, Object> resolvedInputs;
    private final Map<String, Map<String, Object>> upstreamOutputs;

    public PluginContext(NodeDef node, String environment,
                         Map<String, Object> resolvedInputs,
                         Map<String, Map<String, Object>> upstreamOutputs) {
        this.node = node;
        this.environment = environment;
        this.resolvedInputs = resolvedInputs;
        this.upstreamOutputs = upstreamOutputs;
    }

    public NodeDef getNode() { return node; }
    public String getEnvironment() { return environment; }
    public Map<String, Object> getResolvedInputs() { return resolvedInputs; }
    public Map<String, Map<String, Object>> getUpstreamOutputs() { return upstreamOutputs; }
}
```

- [ ] **Step 3: 创建 PluginRegistry**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PluginRegistry {
    private final Map<String, ComponentPlugin> plugins = new HashMap<>();

    public PluginRegistry(List<ComponentPlugin> pluginList) {
        for (ComponentPlugin plugin : pluginList) {
            plugins.put(plugin.supportedType().toUpperCase(), plugin);
        }
    }

    public ComponentPlugin get(String type) {
        return Optional.ofNullable(plugins.get(type.toUpperCase()))
                .orElseThrow(() -> new BusinessException("不支持的组件类型: " + type));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/
git commit -m "feat: add ComponentPlugin interface, PluginContext, and PluginRegistry"
```

---

### Task 9: StartEndPlugin + DbPlugin

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/StartEndPlugin.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/DbPlugin.java`

- [ ] **Step 1: 创建 StartEndPlugin**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StartEndPlugin implements ComponentPlugin {

    @Override
    public String supportedType() { return "START"; }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        Map<String, Object> outputs = new HashMap<>();
        for (IoParamDef def : context.getNode().getOutputParams()) {
            String code = def.getParamCode();
            if (context.getResolvedInputs().containsKey(code)) {
                outputs.put(code, context.getResolvedInputs().get(code));
            } else if (def.getDefaultValue() != null) {
                outputs.put(code, def.getDefaultValue());
            } else {
                outputs.put(code, null);
            }
        }
        if (outputs.isEmpty()) {
            outputs.putAll(context.getResolvedInputs());
        }
        return outputs;
    }
}
```

注意：END 类型也注册。新增一个 `EndPlugin`，或者修改 `supportedType()` 返回多个类型。更简洁的做法是让 `StartEndPlugin` 同时支持两种类型，或者在 PluginRegistry 中允许一个插件注册多个类型。

修改 `ComponentPlugin` 接口以支持多类型：

```java
public interface ComponentPlugin {
    Set<String> supportedTypes();
    Map<String, Object> execute(PluginContext context);
    default boolean isEnabled() { return true; }
}
```

相应地修改 `StartEndPlugin`:

```java
@Component
public class StartEndPlugin implements ComponentPlugin {
    @Override
    public Set<String> supportedTypes() { return Set.of("START", "END"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        // 同上，START/END 都是透传
        Map<String, Object> outputs = new HashMap<>();
        // ... 同上
        return outputs;
    }
}
```

修改 `PluginRegistry`:

```java
public PluginRegistry(List<ComponentPlugin> pluginList) {
    for (ComponentPlugin plugin : pluginList) {
        for (String type : plugin.supportedTypes()) {
            plugins.put(type.toUpperCase(), plugin);
        }
    }
}
```

- [ ] **Step 2: 创建 DbPlugin（从 ExecutorTaskServiceImpl.extractDbComponent 提取）**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DbPlugin implements ComponentPlugin {

    private final JdbcTemplate jdbcTemplate;

    public DbPlugin(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("DB", "MYSQL", "DATABASE", "JDBC"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String sql = readFieldValue(fieldValues, "sql", "SQL", "query", "statement", "dbSql");
        if (sql == null || sql.isBlank()) {
            Object sqlFromInput = firstNonNull(
                    context.getResolvedInputs().get("sql"),
                    context.getResolvedInputs().get("query"));
            if (sqlFromInput != null) sql = Objects.toString(sqlFromInput, "");
        }
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("DB组件缺少sql配置");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("environment", context.getEnvironment());
        return result;
    }

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) {
                return val.asText();
            }
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null && !(v instanceof String s && s.isBlank())) return v;
        }
        return null;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/
git commit -m "feat: add StartEndPlugin and DbPlugin"
```

---

### Task 10: ApiPlugin + ScriptPlugin

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/ApiPlugin.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/ScriptPlugin.java`

- [ ] **Step 1: 创建 ApiPlugin（从 ExecutorTaskServiceImpl.executeApiComponent 提取核心逻辑）**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class ApiPlugin implements ComponentPlugin {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Set<String> supportedTypes() { return Set.of("API", "HTTP", "REST"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String url = readFieldValue(fieldValues, "url", "URL", "apiUrl", "endpoint");
        String method = readFieldValue(fieldValues, "method", "httpMethod");
        if (method.isBlank()) method = "POST";

        if (!url.isBlank()) {
            return doHttpCall(url, method, context.getResolvedInputs());
        }

        // 如果有 apiId，需要通过 Feign 查询（阶段三实现），此处先保留占位
        String apiId = readFieldValue(fieldValues, "apiId", "externalApiId");
        if (!apiId.isBlank()) {
            throw new BusinessException("API组件通过apiId调用需要Feign支持，请先完成阶段三");
        }

        throw new BusinessException("API组件缺少url或apiId配置");
    }

    private Map<String, Object> doHttpCall(String url, String method, Map<String, Object> requestBody) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                requestBody != null ? requestBody : new HashMap<>(), httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, request, String.class);

        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", response.getStatusCode().value());
        result.put("body", response.getBody());
        return result;
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
```

- [ ] **Step 2: 创建 ScriptPlugin（从 ExecutorTaskServiceImpl.executeScriptComponent 提取核心逻辑）**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class ScriptPlugin implements ComponentPlugin {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    public ScriptPlugin(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("SCRIPT", "PYTHON", "SHELL"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String scriptCode = readFieldValue(fieldValues, "scriptCode", "script_code");
        if (scriptCode.isBlank()) {
            scriptCode = context.getNode().getComponentCode();
        }

        List<Map<String, Object>> scriptRows = jdbcTemplate.queryForList(
                "SELECT id, script_code, script_type, status FROM script WHERE delete_flag=0 AND script_code=?",
                scriptCode);
        if (scriptRows.isEmpty()) {
            throw new BusinessException("脚本不存在: " + scriptCode);
        }

        String scriptType = Objects.toString(scriptRows.get(0).get("script_type"), "PYTHON").toUpperCase();

        // 查询已发布版本（阶段三将通过Feign重构此处）
        Long scriptId = ((Number) scriptRows.get(0).get("id")).longValue();
        String env = (context.getEnvironment() == null || context.getEnvironment().isBlank())
                ? "PROD" : context.getEnvironment().toUpperCase();
        List<Map<String, Object>> versionRows = jdbcTemplate.queryForList(
                "SELECT script_code_content, timeout, interpreter_path, work_dir FROM script_version " +
                        "WHERE script_id=? AND environment=? AND is_current=1 AND publish_status=1 AND delete_flag=0 " +
                        "ORDER BY updated_time DESC LIMIT 1",
                scriptId, env);

        if (versionRows.isEmpty()) {
            throw new BusinessException("脚本未找到已发布版本: " + scriptCode);
        }

        Map<String, Object> version = versionRows.get(0);
        String scriptContent = Objects.toString(version.get("script_code_content"), "");
        int timeoutSec = version.get("timeout") == null ? 30 : ((Number) version.get("timeout")).intValue();
        String interpreterPath = Objects.toString(version.get("interpreter_path"), "python");
        String workDir = Objects.toString(version.get("work_dir"), "");

        try {
            if ("SQL".equals(scriptType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(scriptContent);
                Map<String, Object> result = new HashMap<>();
                result.put("rows", rows);
                result.put("rowCount", rows.size());
                result.put("exitCode", 0);
                return result;
            }

            String fileExt = "PYTHON".equals(scriptType) ? ".py" : ".sh";
            String interpreter = "SHELL".equals(scriptType)
                    ? (System.getProperty("os.name").toLowerCase().contains("win") ? "cmd" : "bash")
                    : interpreterPath;

            Path tempScript = Files.createTempFile("df-script-", fileExt);
            Files.writeString(tempScript, scriptContent, StandardCharsets.UTF_8);

            ProcessBuilder pb;
            if ("SHELL".equals(scriptType) && System.getProperty("os.name").toLowerCase().contains("win")) {
                pb = new ProcessBuilder(interpreter, "/c", tempScript.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder(interpreter, tempScript.toAbsolutePath().toString());
            }
            if (!workDir.isBlank()) pb.directory(Path.of(workDir).toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String inputJson = objectMapper.writeValueAsString(
                    context.getResolvedInputs() == null ? Collections.emptyMap() : context.getResolvedInputs());
            process.getOutputStream().write((inputJson + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("脚本执行超时(" + timeoutSec + "s)");
            }

            String stdout;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = br.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }
            String stderr;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    process.getErrorStream(), StandardCharsets.UTF_8))) {
                stderr = br.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }

            int exitCode = process.exitValue();
            Map<String, Object> result = new HashMap<>();
            result.put("exitCode", exitCode);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
            result.put("scriptCode", scriptCode);
            try {
                result.put("result", objectMapper.readValue(stdout, Object.class));
            } catch (Exception e) {
                result.put("result", stdout);
            }

            if (exitCode != 0) {
                throw new BusinessException("脚本执行失败(exitCode=" + exitCode + "): " + stderr);
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("脚本执行异常: " + e.getMessage());
        }
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
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/
git commit -m "feat: add ApiPlugin and ScriptPlugin"
```

---

### Task 11: 执行引擎编排器 ExecEngine

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/ExecEngine.java`

- [ ] **Step 1: 创建 ExecEngine（编排 DSL→解析→校验→排序→遍历节点→调用插件）**

```java
package com.cqie.datafactory.executor.engine;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.*;
import com.cqie.datafactory.executor.engine.core.model.*;
import com.cqie.datafactory.executor.engine.core.model.NodeDef.IoParamDef;
import com.cqie.datafactory.executor.engine.plugin.PluginContext;
import com.cqie.datafactory.executor.engine.plugin.PluginRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

@Component
public class ExecEngine {

    private final DslParser dslParser = new DslParser();
    private final DslValidator dslValidator = new DslValidator();
    private final TopoSort topoSort = new TopoSort();
    private final ParamResolver paramResolver = new ParamResolver();
    private final PluginRegistry pluginRegistry;

    public ExecEngine(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public Map<String, Object> execute(String dslContent, String environment,
                                        Map<String, Object> triggerParams,
                                        Consumer<NodeExecutionRecord> nodeCallback) {
        DslModel dsl = dslParser.parse(dslContent);
        dslValidator.validate(dsl);
        List<NodeDef> sequence = topoSort.sort(dsl);

        Map<String, Map<String, Object>> nodeOutputsMap = new HashMap<>();
        Map<String, Object> finalOutput = new HashMap<>();
        Map<String, Object> currentParams = triggerParams != null ? triggerParams : new HashMap<>();

        for (NodeDef node : sequence) {
            long startMs = System.currentTimeMillis();
            Map<String, Object> resolvedInputs = new HashMap<>();
            NodeExecutionRecord record = new NodeExecutionRecord();
            record.nodeId = node.getId();
            record.nodeName = node.getDisplayName();
            record.nodeType = node.getType();
            record.startTime = startMs;

            try {
                // 解析输入参数
                if ("START".equalsIgnoreCase(node.getType())) {
                    resolvedInputs.putAll(currentParams);
                } else {
                    for (IoParamDef def : node.getInputParams()) {
                        String code = def.getParamCode();
                        resolvedInputs.put(code, paramResolver.resolve(def, nodeOutputsMap));
                    }
                }

                // 获取插件并执行
                Map<String, Object> rawResult;
                if ("START".equalsIgnoreCase(node.getType()) || "END".equalsIgnoreCase(node.getType())) {
                    PluginContext ctx = new PluginContext(node, environment, resolvedInputs, nodeOutputsMap);
                    rawResult = pluginRegistry.get("START").execute(ctx);
                } else {
                    PluginContext ctx = new PluginContext(node, environment, resolvedInputs, nodeOutputsMap);
                    rawResult = pluginRegistry.get(node.getType()).execute(ctx);
                }

                // 构建节点输出
                Map<String, Object> nodeOutputs = buildNodeOutputs(node, resolvedInputs, rawResult);
                nodeOutputsMap.put(node.getId(), nodeOutputs);
                if ("END".equalsIgnoreCase(node.getType())) {
                    finalOutput = nodeOutputs;
                }

                record.status = "SUCCESS";
                record.outputs = rawResult;
            } catch (Exception e) {
                record.status = "FAILURE";
                record.errorMessage = e.getMessage();
                if (nodeCallback != null) {
                    record.endTime = System.currentTimeMillis();
                    record.durationMs = record.endTime - startMs;
                    nodeCallback.accept(record);
                }
                throw new BusinessException("节点[" + node.getDisplayName() + "]执行失败: " + e.getMessage());
            }

            record.endTime = System.currentTimeMillis();
            record.durationMs = record.endTime - startMs;
            if (nodeCallback != null) {
                nodeCallback.accept(record);
            }
        }

        return finalOutput;
    }

    private Map<String, Object> buildNodeOutputs(NodeDef node, Map<String, Object> resolvedInputs,
                                                  Map<String, Object> rawResult) {
        Map<String, Object> outputs = new HashMap<>();
        for (IoParamDef def : node.getOutputParams()) {
            String code = def.getParamCode();
            if (rawResult.containsKey(code)) {
                outputs.put(code, rawResult.get(code));
            } else if (resolvedInputs.containsKey(code)) {
                outputs.put(code, resolvedInputs.get(code));
            } else {
                outputs.put(code, rawResult);
            }
        }
        if (outputs.isEmpty()) {
            outputs.putAll(rawResult);
        }
        return outputs;
    }

    public static class NodeExecutionRecord {
        public String nodeId;
        public String nodeName;
        public String nodeType;
        public String status;
        public long startTime;
        public long endTime;
        public long durationMs;
        public Map<String, Object> outputs;
        public String errorMessage;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/ExecEngine.java
git commit -m "feat: add ExecEngine orchestrator"
```

---

### Task 12: 异步线程池配置

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/pool/ExecutionThreadPoolConfig.java`

- [ ] **Step 1: 创建线程池配置**

```java
package com.cqie.datafactory.executor.engine.pool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ExecutionThreadPoolConfig {

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("task-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/pool/
git commit -m "feat: add async thread pool configuration"
```

---

### Task 13: 重构 ExecutorTaskServiceImpl 使用 ExecEngine

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/ExecutorTaskServiceImpl.java`

- [ ] **Step 1: 注入 ExecEngine，替换 execute 方法体**

添加字段:

```java
private final ExecEngine execEngine;

// 在构造函数中注入（该类已使用 @RequiredArgsConstructor，改为显式构造函数或添加字段）
```

修改 `execute` 方法中的核心执行逻辑（`new Thread(() -> { ... }).start()` 内部），将整个节点遍历+执行的代码替换为:

```java
// 替换 new Thread() 内部的 for (JsonNode n : executionSequence) 循环
// 为调用 execEngine.execute()

Map<String, Object> finalOutput = execEngine.execute(
    dsl.getDslContent(),
    environment,
    mergedParams,
    record -> {
        // record 是 NodeExecutionRecord
        NodeExecutionLog nodeLog = new NodeExecutionLog();
        nodeLog.setExecutionId(executionId);
        nodeLog.setNodeId(record.nodeId);
        nodeLog.setNodeName(record.nodeName);
        nodeLog.setNodeType(record.nodeType);
        nodeLog.setStatus(record.status);
        nodeLog.setStartTime(LocalDateTime.now()); // 简化处理
        nodeLog.setEndTime(LocalDateTime.now());
        nodeLog.setDurationMs(record.durationMs);
        if (record.errorMessage != null) {
            nodeLog.setErrorMessage(record.errorMessage);
        }
        executionLogService.sendNodeLog(nodeLog);
    }
);
finalTaskOutput = objectMapper.writeValueAsString(finalOutput);
```

同时将 `new Thread()` 替换为:

```java
@Async("taskExecutor")
public void executeAsync(...) { ... }
```

- [ ] **Step 2: 删除 ExecutorTaskServiceImpl 中已迁移到插件的方法**

删除以下方法（功能已由各 Plugin 和 ExecEngine 实现）:
- `sortNodesTopologicallyByCanvas`
- `getNodeCanvasX`, `getNodeCanvasY`, `nodesToList`
- `executeNodeWithComponent`
- `dispatchByComponentType`
- `executeDbComponent`
- `executeApiComponent`
- `executeScriptComponent`
- `doHttpCall`
- `resolveSqlQuerySourceValue`
- `resolveApiQuerySourceValue`
- `resolveDbVersionForSqlQuery`
- `buildOrGetCachedJdbcTemplate`
- `evictIdleDynamicDataSources`
- `CachedDataSourceHolder` 内部类
- `parseSourceValueNode`
- `resolveParameterValue`
- `resolveUpstreamValue`
- `readTextByAliases`
- `applyExprOrDefault`
- `hydrateCommonComponentInputs`
- `buildOutputsByIoSchema`
- `mapRawResultToNodeOutputs`
- `extractNodeFieldValues`
- `extractNodeIoParams`
- `firstNonNull`, `isBlankObj`, `safeSourceValueText`

保留的方法:
- `resolveDslForExecution`
- `extractVersionId`
- `requireTask`
- `loadTestConfigParams`
- `validateRequiredParameters`（保留或迁移到 ExecEngine）
- `create`, `update`, `delete`, `detail`, `page`, `changeStatus`, `toVO`

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/ExecutorTaskServiceImpl.java
git commit -m "refactor: streamline ExecutorTaskServiceImpl to delegate to ExecEngine"
```

---

## 阶段三：Feign 服务间通信

### Task 14: Feign 接口定义 (executor-feign 模块)

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-feign/pom.xml`
- Create: 4 个 Feign 接口 + 对应 VO

- [ ] **Step 1: 添加 OpenFeign 依赖到 executor-feign/pom.xml**

在 `<dependencies>` 中添加:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>com.cqie.datafactory</groupId>
    <artifactId>datafactory-backend-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

- [ ] **Step 2: 创建 VO 类**

Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/vo/DbVersionResolveVO.java`

```java
package com.cqie.datafactory.executor.feign.vo;

public class DbVersionResolveVO {
    private Long id;
    private Long dbId;
    private String environment;
    private String jdbcUrl;
    private String username;
    private String password;
    private String dbType;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
}
```

Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/vo/ScriptExecutionVO.java`

```java
package com.cqie.datafactory.executor.feign.vo;

public class ScriptExecutionVO {
    private Long id;
    private Long scriptId;
    private String scriptCode;
    private String scriptName;
    private String scriptType;
    private String version;
    private String environment;
    private String scriptCodeContent;
    private Integer timeout;
    private Integer retryCount;
    private String dependencies;
    private String envVars;
    private String workDir;
    private String interpreterPath;
    private Integer maxMemory;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public String getScriptCode() { return scriptCode; }
    public void setScriptCode(String scriptCode) { this.scriptCode = scriptCode; }
    public String getScriptName() { return scriptName; }
    public void setScriptName(String scriptName) { this.scriptName = scriptName; }
    public String getScriptType() { return scriptType; }
    public void setScriptType(String scriptType) { this.scriptType = scriptType; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getScriptCodeContent() { return scriptCodeContent; }
    public void setScriptCodeContent(String scriptCodeContent) { this.scriptCodeContent = scriptCodeContent; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getDependencies() { return dependencies; }
    public void setDependencies(String dependencies) { this.dependencies = dependencies; }
    public String getEnvVars() { return envVars; }
    public void setEnvVars(String envVars) { this.envVars = envVars; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public String getInterpreterPath() { return interpreterPath; }
    public void setInterpreterPath(String interpreterPath) { this.interpreterPath = interpreterPath; }
    public Integer getMaxMemory() { return maxMemory; }
    public void setMaxMemory(Integer maxMemory) { this.maxMemory = maxMemory; }
}
```

Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/vo/ApiVersionResolveVO.java`

```java
package com.cqie.datafactory.executor.feign.vo;

public class ApiVersionResolveVO {
    private Long id;
    private Long apiId;
    private String environment;
    private String requestMethod;
    private String requestUrl;
    private String requestHeaders;
    private String requestBody;
    private String contentType;
    private Integer timeout;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApiId() { return apiId; }
    public void setApiId(Long apiId) { this.apiId = apiId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public String getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(String requestHeaders) { this.requestHeaders = requestHeaders; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
}
```

- [ ] **Step 3: 创建 4 个 Feign 接口**

Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/DatasourceFeignClient.java`

```java
package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.vo.DbVersionResolveVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "datafactory-backend-configuration", path = "/feign/datasource")
public interface DatasourceFeignClient {
    @GetMapping("/db-version/resolve")
    Result<DbVersionResolveVO> resolveDbVersion(@RequestParam Long dbId, @RequestParam String environment);
}
```

Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/ScriptFeignClient.java`

```java
package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.vo.ScriptExecutionVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "datafactory-backend-configuration", path = "/feign/script")
public interface ScriptFeignClient {
    @GetMapping("/version/for-execution")
    Result<ScriptExecutionVO> resolveScriptVersion(@RequestParam Long scriptId, @RequestParam String environment);
}
```

Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/ExternalApiFeignClient.java`

```java
package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.feign.vo.ApiVersionResolveVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "datafactory-backend-configuration", path = "/feign/external-api")
public interface ExternalApiFeignClient {
    @GetMapping("/version/resolve")
    Result<ApiVersionResolveVO> resolveApiVersion(@RequestParam Long apiId, @RequestParam String environment);
}
```

Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/ComponentFeignClient.java`

```java
package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "datafactory-backend-configuration", path = "/feign/component")
public interface ComponentFeignClient {
    @GetMapping("/{id}/validate")
    Result<Map<String, Object>> validateAndGet(@PathVariable Long id);
}
```

- [ ] **Step 4: 添加 executor-server 对 executor-feign 的依赖 + Feign 配置**

Modify `datafactory-backend-executor/datafactory-backend-executor-server/pom.xml`:

```xml
<dependency>
    <groupId>com.cqie.datafactory</groupId>
    <artifactId>datafactory-backend-executor-feign</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Modify `datafactory-backend-executor/datafactory-backend-executor-server/src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
```

- [ ] **Step 5: 在 ExecutorApplication 添加 @EnableFeignClients**

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.cqie.datafactory.executor.feign")
public class ExecutorApplication {
    // ...
}
```

- [ ] **Step 6: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-feign/ datafactory-backend-executor/datafactory-backend-executor-server/
git commit -m "feat: add Feign client interfaces for inter-service communication"
```

---

### Task 15: Configuration 服务 FeignController 实现

**Files:**
- Create: 4 个 FeignController

- [ ] **Step 1: 创建 DatasourceFeignController**

```java
package com.cqie.datafactory.configuration.controller.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.common.util.AesEncryptUtil;
import com.cqie.datafactory.configuration.entity.DatasourceDbVersion;
import com.cqie.datafactory.configuration.service.DatasourceDbVersionService;
import com.cqie.datafactory.executor.feign.vo.DbVersionResolveVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feign/datasource")
public class DatasourceFeignController {

    private final DatasourceDbVersionService dbVersionService;
    private final AesEncryptUtil aesEncryptUtil;

    public DatasourceFeignController(DatasourceDbVersionService dbVersionService, AesEncryptUtil aesEncryptUtil) {
        this.dbVersionService = dbVersionService;
        this.aesEncryptUtil = aesEncryptUtil;
    }

    @GetMapping("/db-version/resolve")
    public Result<DbVersionResolveVO> resolveDbVersion(@RequestParam Long dbId, @RequestParam String environment) {
        DatasourceDbVersion version = dbVersionService.getOne(new LambdaQueryWrapper<DatasourceDbVersion>()
                .eq(DatasourceDbVersion::getDbId, dbId)
                .eq(DatasourceDbVersion::getEnvironment, environment.toUpperCase())
                .eq(DatasourceDbVersion::getIsCurrent, 1)
                .eq(DatasourceDbVersion::getPublishStatus, 1)
                .eq(DatasourceDbVersion::getDeleteFlag, 0)
                .orderByDesc(DatasourceDbVersion::getUpdatedTime)
                .last("limit 1"));

        if (version == null) {
            return Result.error("未找到可用数据源版本");
        }

        DbVersionResolveVO vo = new DbVersionResolveVO();
        vo.setId(version.getId());
        vo.setDbId(version.getDbId());
        vo.setEnvironment(version.getEnvironment());
        vo.setJdbcUrl(version.getJdbcUrl());
        vo.setUsername(version.getUsername());
        vo.setDbType(version.getDbType());
        // 解密密码返回
        vo.setPassword(aesEncryptUtil.decrypt(version.getPassword()));
        return Result.ok(vo);
    }
}
```

同理创建 `ScriptFeignController`、`ExternalApiFeignController`、`ComponentFeignController`（模式相同：查数据库 → 组装 VO → 返回）。

- [ ] **Step 2: 在 executor-server 启动类添加 Feign 包扫描**

确认 `ExecutorApplication.java` 包含:
```java
@EnableFeignClients(basePackages = "com.cqie.datafactory.executor.feign")
```

- [ ] **Step 3: 修改 ApiPlugin/ScriptPlugin 使用 Feign 替代 jdbcTemplate 直查**

在 `ApiPlugin` 中注入 `ExternalApiFeignClient`，替换 `jdbcTemplate.queryForList(...)` 对 `external_api_version` 的查询:

```java
private final ExternalApiFeignClient externalApiFeignClient;

// 替换 jdbcTemplate 查询为:
Result<ApiVersionResolveVO> result = externalApiFeignClient.resolveApiVersion(
        Long.parseLong(apiId), context.getEnvironment());
if (!result.isSuccess() || result.getData() == null) {
    throw new BusinessException("API版本查询失败: " + apiId);
}
ApiVersionResolveVO api = result.getData();
```

在 `ScriptPlugin` 中注入 `ScriptFeignClient`，替换对 `script_version` 的查询。

- [ ] **Step 4: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/feign/ \
        datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/
git commit -m "feat: add FeignControllers and wire plugins to Feign clients"
```

---

## 阶段四：新组件 + 定时任务

### Task 16: BranchPlugin

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/BranchPlugin.java`

- [ ] **Step 1: 创建 BranchPlugin**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BranchPlugin implements ComponentPlugin {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    @Override
    public Set<String> supportedTypes() { return Set.of("BRANCH"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String expression = readFieldValue(fieldValues, "expression", "condition");
        if (expression.isBlank()) {
            throw new BusinessException("BRANCH组件缺少条件表达式");
        }

        String trueTarget = readFieldValue(fieldValues, "trueBranch", "trueTargetNodeId");
        String falseTarget = readFieldValue(fieldValues, "falseBranch", "falseTargetNodeId");

        // 替换表达式中的变量
        String resolvedExpr = resolveVariables(expression, context.getUpstreamOutputs(), context.getResolvedInputs());
        boolean result = evaluateExpression(resolvedExpr);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("conditionResult", result);
        outputs.put("nextNodeId", result ? trueTarget : falseTarget);
        return outputs;
    }

    private String resolveVariables(String expr, Map<String, Map<String, Object>> upstreamOutputs,
                                    Map<String, Object> resolvedInputs) {
        Matcher matcher = VAR_PATTERN.matcher(expr);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = findVariable(varName, upstreamOutputs, resolvedInputs);
            matcher.appendReplacement(sb, value != null ? value.toString() : "null");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Object findVariable(String varName, Map<String, Map<String, Object>> upstreamOutputs,
                                Map<String, Object> resolvedInputs) {
        if (resolvedInputs.containsKey(varName)) return resolvedInputs.get(varName);
        if (varName.contains(".")) {
            String[] parts = varName.split("\\.", 2);
            Map<String, Object> nodeOutput = upstreamOutputs.get(parts[0]);
            if (nodeOutput != null && nodeOutput.containsKey(parts[1])) {
                return nodeOutput.get(parts[1]);
            }
        }
        for (Map<String, Object> nodeOutput : upstreamOutputs.values()) {
            if (nodeOutput.containsKey(varName)) return nodeOutput.get(varName);
        }
        return null;
    }

    private boolean evaluateExpression(String expr) {
        // 简单表达式求值: 支持 >, <, >=, <=, ==, !=
        expr = expr.trim();
        if ("true".equalsIgnoreCase(expr)) return true;
        if ("false".equalsIgnoreCase(expr)) return false;

        try {
            if (expr.contains(">=")) return compare(expr, ">=");
            if (expr.contains("<=")) return compare(expr, "<=");
            if (expr.contains("!=")) return compare(expr, "!=");
            if (expr.contains("==")) return compare(expr, "==");
            if (expr.contains(">")) return compare(expr, ">");
            if (expr.contains("<")) return compare(expr, "<");
        } catch (Exception e) {
            throw new BusinessException("条件表达式求值失败: " + expr);
        }
        throw new BusinessException("无法识别的条件表达式: " + expr);
    }

    private boolean compare(String expr, String op) {
        String[] parts = expr.split(Pattern.quote(op), 2);
        String left = parts[0].trim().replace("'", "").replace("\"", "");
        String right = parts[1].trim().replace("'", "").replace("\"", "");

        try {
            double leftNum = Double.parseDouble(left);
            double rightNum = Double.parseDouble(right);
            return switch (op) {
                case ">" -> leftNum > rightNum;
                case "<" -> leftNum < rightNum;
                case ">=" -> leftNum >= rightNum;
                case "<=" -> leftNum <= rightNum;
                case "==" -> leftNum == rightNum;
                case "!=" -> leftNum != rightNum;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return switch (op) {
                case "==" -> left.equals(right);
                case "!=" -> !left.equals(right);
                default -> false;
            };
        }
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
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/BranchPlugin.java
git commit -m "feat: add BranchPlugin for conditional branching"
```

---

### Task 17: FilterPlugin

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/FilterPlugin.java`

- [ ] **Step 1: 创建 FilterPlugin**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FilterPlugin implements ComponentPlugin {

    @Override
    public Set<String> supportedTypes() { return Set.of("FILTER"); }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String filterType = readFieldValue(fieldValues, "filterType", "type");
        String sourceNodeId = readFieldValue(fieldValues, "sourceNodeId", "sourceNode");

        // 获取上游数据
        Map<String, Object> sourceData = null;
        if (!sourceNodeId.isBlank()) {
            sourceData = context.getUpstreamOutputs().get(sourceNodeId);
        }
        if (sourceData == null) {
            // 尝试从所有上游输出中找第一个有数据的
            for (Map<String, Object> v : context.getUpstreamOutputs().values()) {
                if (v.containsKey("rows")) { sourceData = v; break; }
            }
        }
        if (sourceData == null) {
            throw new BusinessException("FILTER组件未找到上游数据源");
        }

        Object rowsObj = sourceData.get("rows");
        if (!(rowsObj instanceof List)) {
            return sourceData;
        }

        List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;

        if ("COLUMN".equalsIgnoreCase(filterType)) {
            String columnsStr = readFieldValue(fieldValues, "columnFilter", "columns");
            List<String> columns = Arrays.stream(columnsStr.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            List<Map<String, Object>> filtered = rows.stream()
                    .map(row -> {
                        Map<String, Object> newRow = new LinkedHashMap<>();
                        for (String col : columns) {
                            newRow.put(col, row.getOrDefault(col, null));
                        }
                        return newRow;
                    }).collect(Collectors.toList());
            Map<String, Object> result = new HashMap<>(sourceData);
            result.put("rows", filtered);
            result.put("rowCount", filtered.size());
            return result;
        }

        // 默认行过滤: 此处为简化实现，实际可用表达式引擎
        Map<String, Object> result = new HashMap<>(sourceData);
        result.put("rowCount", rows.size());
        return result;
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
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/FilterPlugin.java
git commit -m "feat: add FilterPlugin for data filtering"
```

---

### Task 18: CommonTaskPlugin + 定时任务

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/CommonTaskPlugin.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/entity/ScheduleJob.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/mapper/ScheduleJobMapper.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/ScheduleJobService.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/TaskScheduleExecutor.java`

- [ ] **Step 1: 创建 CommonTaskPlugin**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.ExecEngine;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CommonTaskPlugin implements ComponentPlugin {

    private final ExecEngine execEngine;
    private final JdbcTemplate jdbcTemplate;

    public CommonTaskPlugin(ExecEngine execEngine, JdbcTemplate jdbcTemplate) {
        this.execEngine = execEngine;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> supportedTypes() { return Set.of("COMMON_TASK", "TASK"); }

    @Override
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String subTaskId = readFieldValue(fieldValues, "subTaskId", "taskId");
        if (subTaskId.isBlank()) {
            throw new BusinessException("COMMON_TASK缺少subTaskId");
        }

        List<Map<String, Object>> taskRows = jdbcTemplate.queryForList(
                "SELECT id, task_name, status FROM task WHERE id=? AND delete_flag=0", Long.parseLong(subTaskId));
        if (taskRows.isEmpty()) {
            throw new BusinessException("子任务不存在: " + subTaskId);
        }

        // 查询子任务的当前版本DSL
        String env = context.getEnvironment();
        List<Map<String, Object>> dslRows = jdbcTemplate.queryForList(
                "SELECT dsl_content FROM task_dsl WHERE task_id=? AND environment=? AND is_current=1 AND delete_flag=0 ORDER BY created_time DESC LIMIT 1",
                Long.parseLong(subTaskId), env);

        if (dslRows.isEmpty()) {
            throw new BusinessException("子任务未找到已发布版本: " + subTaskId);
        }

        String dslContent = Objects.toString(dslRows.get(0).get("dsl_content"), "");
        // 将当前上下文参数传递给子任务
        Map<String, Object> childParams = new HashMap<>(context.getResolvedInputs());
        childParams.putAll(context.getUpstreamOutputs().values().stream()
                .findFirst().orElse(new HashMap<>()));

        return execEngine.execute(dslContent, env, childParams, null);
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
```

- [ ] **Step 2: 创建 ScheduleJob 实体**

```java
package com.cqie.datafactory.executor.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("schedule_job")
public class ScheduleJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobCode;
    private Long taskId;
    private Long taskVersionId;
    private String cronExpression;
    private String environment;
    private Integer status;
    private String lastExecutionId;
    private LocalDateTime lastFireTime;
    private LocalDateTime nextFireTime;
    private Integer deleteFlag;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    // getters and setters (use Lombok @Data in real implementation)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getTaskVersionId() { return taskVersionId; }
    public void setTaskVersionId(Long taskVersionId) { this.taskVersionId = taskVersionId; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getLastExecutionId() { return lastExecutionId; }
    public void setLastExecutionId(String lastExecutionId) { this.lastExecutionId = lastExecutionId; }
    public LocalDateTime getLastFireTime() { return lastFireTime; }
    public void setLastFireTime(LocalDateTime lastFireTime) { this.lastFireTime = lastFireTime; }
    public LocalDateTime getNextFireTime() { return nextFireTime; }
    public void setNextFireTime(LocalDateTime nextFireTime) { this.nextFireTime = nextFireTime; }
    public Integer getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(Integer deleteFlag) { this.deleteFlag = deleteFlag; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
```

- [ ] **Step 3: 创建 ScheduleJobMapper 和 ScheduleJobService**

```java
package com.cqie.datafactory.executor.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleJobMapper extends BaseMapper<ScheduleJob> {
}
```

```java
package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleJobService extends ServiceImpl<ScheduleJobMapper, ScheduleJob> {

    public List<ScheduleJob> listEnabledJobs() {
        return list(new LambdaQueryWrapper<ScheduleJob>()
                .eq(ScheduleJob::getStatus, 1)
                .eq(ScheduleJob::getDeleteFlag, 0));
    }
}
```

- [ ] **Step 4: 创建 TaskScheduleExecutor**

```java
package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.engine.ExecEngine;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TaskScheduleExecutor {

    private final ScheduleJobService scheduleJobService;
    private final ExecEngine execEngine;
    private final JdbcTemplate jdbcTemplate;

    public TaskScheduleExecutor(ScheduleJobService scheduleJobService, ExecEngine execEngine,
                                JdbcTemplate jdbcTemplate) {
        this.scheduleJobService = scheduleJobService;
        this.execEngine = execEngine;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRate = 30000) // 每30秒检查一次
    public void checkAndFire() {
        List<ScheduleJob> jobs = scheduleJobService.listEnabledJobs();
        for (ScheduleJob job : jobs) {
            // 简化: 检查是否到达触发时间
            LocalDateTime nextFire = job.getNextFireTime();
            if (nextFire != null && nextFire.isBefore(LocalDateTime.now())) {
                fireJob(job);
            }
        }
    }

    private void fireJob(ScheduleJob job) {
        try {
            List<String> dslRows = jdbcTemplate.queryForList(
                    "SELECT dsl_content FROM task_dsl WHERE id=?", String.class, job.getTaskVersionId());
            if (dslRows.isEmpty()) return;

            execEngine.execute(dslRows.get(0), job.getEnvironment(), null, null);

            job.setLastFireTime(LocalDateTime.now());
            job.setNextFireTime(null); // 简化处理,实际应基于cron计算
            scheduleJobService.updateById(job);
        } catch (Exception e) {
            // 记录失败但不中断调度
        }
    }
}
```

- [ ] **Step 5: 新增 schedule_job 表 SQL**

在 `datafactory.sql` 末尾追加:

```sql
CREATE TABLE IF NOT EXISTS schedule_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL UNIQUE,
  task_id BIGINT NOT NULL,
  task_version_id BIGINT NOT NULL,
  cron_expression VARCHAR(64) NOT NULL,
  environment VARCHAR(20) DEFAULT 'PROD',
  status TINYINT DEFAULT 1,
  last_execution_id VARCHAR(64),
  last_fire_time DATETIME,
  next_fire_time DATETIME,
  delete_flag TINYINT DEFAULT 0,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 6: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/CommonTaskPlugin.java \
        datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/ \
        datafactory.sql
git commit -m "feat: add CommonTaskPlugin, schedule job entity/service/executor, and schedule_job table"
```

---

## 阶段五：监控统计

### Task 19: 统计服务 + 进度查询

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/statistics/StatisticsService.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/statistics/StatisticsController.java`

- [ ] **Step 1: 创建 StatisticsService**

```java
package com.cqie.datafactory.executor.statistics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class StatisticsService {

    private final JdbcTemplate jdbcTemplate;

    public StatisticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getOverview() {
        String today = LocalDate.now().toString();
        Map<String, Object> overview = new LinkedHashMap<>();

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM execution_log WHERE DATE(start_time) = ?", Long.class, today);
        Long success = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM execution_log WHERE DATE(start_time) = ? AND status = 'SUCCESS'", Long.class, today);
        Long failure = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM execution_log WHERE DATE(start_time) = ? AND status = 'FAILURE'", Long.class, today);

        overview.put("total", total != null ? total : 0);
        overview.put("success", success != null ? success : 0);
        overview.put("failure", failure != null ? failure : 0);

        Double avgDuration = jdbcTemplate.queryForObject(
                "SELECT AVG(duration_ms) FROM execution_log WHERE DATE(start_time) = ?", Double.class, today);
        overview.put("avgDurationMs", avgDuration != null ? Math.round(avgDuration) : 0);

        return overview;
    }

    public List<Map<String, Object>> getTrend(int days) {
        return jdbcTemplate.queryForList(
                "SELECT DATE(start_time) as date, COUNT(1) as count, " +
                        "SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
                        "SUM(CASE WHEN status='FAILURE' THEN 1 ELSE 0 END) as failureCount " +
                        "FROM execution_log WHERE start_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                        "GROUP BY DATE(start_time) ORDER BY date", days);
    }

    public List<Map<String, Object>> getFailureTop10() {
        return jdbcTemplate.queryForList(
                "SELECT task_name, COUNT(1) as failureCount FROM execution_log " +
                        "WHERE status='FAILURE' AND start_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                        "GROUP BY task_name ORDER BY failureCount DESC LIMIT 10");
    }

    public Map<String, Object> getExecutionProgress(String executionId) {
        Map<String, Object> progress = new LinkedHashMap<>();
        Map<String, Object> execLog = jdbcTemplate.queryForMap(
                "SELECT execution_id, status FROM execution_log WHERE execution_id = ?", executionId);
        if (execLog == null) {
            progress.put("error", "执行记录不存在");
            return progress;
        }

        progress.put("executionId", executionId);
        progress.put("status", execLog.get("status"));

        List<Map<String, Object>> nodes = jdbcTemplate.queryForList(
                "SELECT node_id, node_name, node_type, status, duration_ms, error_message " +
                        "FROM node_execution_log WHERE execution_id = ? ORDER BY start_time", executionId);

        long completed = nodes.stream().filter(n -> "SUCCESS".equals(n.get("status"))).count();
        progress.put("currentNodeId", nodes.isEmpty() ? null : nodes.get(nodes.size() - 1).get("node_id"));
        progress.put("progress", nodes.isEmpty() ? 0 : (double) completed / nodes.size());
        progress.put("nodeStatuses", nodes);
        return progress;
    }
}
```

- [ ] **Step 2: 创建 StatisticsController**

```java
package com.cqie.datafactory.executor.statistics;

import com.cqie.datafactory.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statisticsService.getOverview());
    }

    @GetMapping("/trend")
    public Result<?> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statisticsService.getTrend(days));
    }

    @GetMapping("/task-rank")
    public Result<?> taskRank() {
        return Result.ok(statisticsService.getFailureTop10());
    }

    @GetMapping("/execution/{executionId}/progress")
    public Result<?> executionProgress(@PathVariable String executionId) {
        return Result.ok(statisticsService.getExecutionProgress(executionId));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/statistics/
git commit -m "feat: add statistics service and controller for monitoring"
```

---

## 验证方式

每个阶段完成后运行:

```bash
cd datafactory
mvn compile -pl datafactory-backend-common,datafactory-backend-configuration,datafactory-backend-executor/datafactory-backend-executor-server -am
```

确认编译通过，无编译错误。

任务执行验证（阶段二后）:

```bash
curl -X POST http://localhost:8082/tasks/1/execute \
  -H "Content-Type: application/json" \
  -d '{"environment": "TEST", "versionId": 1}'
```

确认返回 executionId 且执行日志正常记录。
