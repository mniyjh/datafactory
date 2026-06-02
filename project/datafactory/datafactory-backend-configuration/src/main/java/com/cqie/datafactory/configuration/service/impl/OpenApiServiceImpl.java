package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.OpenApiCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.OpenApiVO;
import com.cqie.datafactory.configuration.entity.OpenApi;
import com.cqie.datafactory.configuration.mapper.OpenApiMapper;
import com.cqie.datafactory.configuration.service.OpenApiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class OpenApiServiceImpl extends ServiceImpl<OpenApiMapper, OpenApi> implements OpenApiService {

  @Value("${executor.base-url:http://127.0.0.1:8082}")
  private String executorBaseUrl;
  private final RestTemplate restTemplate = new RestTemplate();

  @Resource
  private JdbcTemplate jdbcTemplate;

  private final ConcurrentHashMap<String, long[]> rateLimitCounters = new ConcurrentHashMap<>();

  @Override
  public PageResult<OpenApiVO> pageApi(PageQuery pageQuery, String keyword) {
    Page<OpenApi> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
    LambdaQueryWrapper<OpenApi> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      wrapper.and(w -> w.like(OpenApi::getApiCode, keyword).or().like(OpenApi::getApiName, keyword));
    }
    wrapper.orderByDesc(OpenApi::getCreatedTime);

    Page<OpenApi> resultPage = this.page(page, wrapper);

    PageResult<OpenApiVO> pageResult = new PageResult<>();
    pageResult.setTotal(resultPage.getTotal());
    pageResult.setRecords(resultPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
    return pageResult;
  }

  @Override
  public void createApi(OpenApiCreateDTO dto) {
    OpenApi api = new OpenApi();

    String apiCode = StringUtils.hasText(dto.getCode()) ? dto.getCode().trim() : generateApiCode();
    long exists = this.count(new LambdaQueryWrapper<OpenApi>().eq(OpenApi::getApiCode, apiCode));
    if (exists > 0) {
      throw new RuntimeException("接口编码已存在: " + apiCode);
    }

    api.setApiCode(apiCode);
    api.setApiName(dto.getName());
    api.setApiPath(dto.getPath());
    api.setApiMethod(dto.getMethod());
    api.setTaskId(dto.getTaskId());
    api.setInputSchema(dto.getInputSchema());
    api.setOutputSchema(dto.getOutputSchema());
    api.setAuthType(dto.getAuthType());
    api.setLimitCount(dto.getLimit());
    api.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
    api.setDescription(dto.getDesc());
    this.save(api);
  }

  @Override
  public void updateApi(Long id, OpenApiCreateDTO dto) {
    OpenApi api = this.getById(id);
    if (api != null) {
      api.setApiName(dto.getName());
      api.setApiPath(dto.getPath());
      api.setApiMethod(dto.getMethod());
      api.setTaskId(dto.getTaskId());
      api.setInputSchema(dto.getInputSchema());
      api.setOutputSchema(dto.getOutputSchema());
      api.setAuthType(dto.getAuthType());
      api.setLimitCount(dto.getLimit());
      api.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
      api.setDescription(dto.getDesc());
      this.updateById(api);
    }
  }

  @Override
  public void deleteApi(Long id) {
    boolean removed = removeById(id);
    if (!removed) {
      throw new RuntimeException("开放接口不存在或已删除");
    }
  }

  @Override
  public void toggleStatus(Long id) {
    OpenApi api = this.getById(id);
    if (api != null) {
      api.setStatus(api.getStatus() == 1 ? 0 : 1);
      this.updateById(api);
    }
  }

  @Override
  public void generateKey(Long id) {
    OpenApi api = this.getById(id);
    if (api != null) {
      api.setAppSecret(UUID.randomUUID().toString().replace("-", ""));
      this.updateById(api);
    }
  }

  @Override
  public Map<String, Object> invokeByCode(String code, String appSecret, Map<String, Object> payload) {
    return doInvoke(code, appSecret, payload, false);
  }

  @Override
  public Map<String, Object> invokeSync(String code, String appSecret, Map<String, Object> payload, long timeoutMs) {
    return doInvoke(code, appSecret, payload, true);
  }

  @Override
  public Map<String, Object> queryResult(String executionId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT status, output_result, error_message, duration_ms FROM execution_log WHERE execution_id = ?",
        executionId);
    if (rows.isEmpty()) {
      Map<String, Object> result = new HashMap<>();
      result.put("status", "NOT_FOUND");
      result.put("message", "执行记录不存在");
      return result;
    }
    Map<String, Object> log = rows.get(0);
    Map<String, Object> result = new HashMap<>();
    result.put("executionId", executionId);
    result.put("status", log.get("status"));
    result.put("durationMs", log.get("duration_ms"));
    if ("SUCCESS".equals(log.get("status"))) {
      result.put("output", log.get("output_result"));
    } else if ("FAILURE".equals(log.get("status"))) {
      result.put("errorMessage", log.get("error_message"));
    }
    return result;
  }

  private Map<String, Object> doInvoke(String code, String appSecret, Map<String, Object> payload, boolean sync) {
    long startMs = System.currentTimeMillis();
    OpenApi api = this.getOne(new LambdaQueryWrapper<OpenApi>().eq(OpenApi::getApiCode, code).last("limit 1"));
    if (api == null) {
      throw new RuntimeException("开放接口不存在: " + code);
    }
    if (api.getStatus() == null || api.getStatus() != 1) {
      throw new RuntimeException("开放接口已禁用");
    }
    if (StringUtils.hasText(api.getAppSecret())) {
      if (!StringUtils.hasText(appSecret) || !api.getAppSecret().equals(appSecret)) {
        throw new RuntimeException("appSecret校验失败");
      }
    }
    if (api.getTaskId() == null) {
      throw new RuntimeException("开放接口未绑定任务");
    }

    // 限流检查
    int limitCount = api.getLimitCount() != null ? api.getLimitCount() : 0;
    if (limitCount > 0) {
      String counterKey = code + "_" + (System.currentTimeMillis() / 1000);
      long[] counter = rateLimitCounters.computeIfAbsent(counterKey, k -> new long[1]);
      synchronized (counter) {
        if (counter[0] >= limitCount) {
          throw new RuntimeException("调用频率超限，当前限制: " + limitCount + "/秒");
        }
        counter[0]++;
      }
    }

    String executeUrl = executorBaseUrl + "/tasks/" + api.getTaskId() + "/execute";
    @SuppressWarnings("unchecked")
    Map<String, Object> executorRes = restTemplate.postForObject(executeUrl, payload == null ? new HashMap<>() : payload, Map.class);

    Object executionId = null;
    if (executorRes != null) {
      Object data = executorRes.get("data");
      if (data instanceof String) {
        executionId = data;
      } else if (data instanceof Map<?, ?> dataMap) {
        executionId = dataMap.get("executionId");
      }
      if (executionId == null && executorRes.get("executionId") != null) {
        executionId = executorRes.get("executionId");
      }
    }
    String execIdStr = executionId != null ? executionId.toString() : null;

    // 记录调用日志
    recordInvokeLog(code, api.getTaskId(), execIdStr, payload, startMs);

    Map<String, Object> result = new HashMap<>();
    result.put("apiCode", api.getApiCode());
    result.put("taskId", api.getTaskId());
    result.put("executionId", execIdStr);
    result.put("message", "触发成功");

    // 同步模式：轮询等待结果
    if (sync && execIdStr != null) {
      long maxWait = 60000;
      long interval = 500;
      long waited = 0;
      while (waited < maxWait) {
        try {
          Thread.sleep(interval);
          waited += interval;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
        List<Map<String, Object>> logRows = jdbcTemplate.queryForList(
            "SELECT status, output_result, error_message, duration_ms FROM execution_log WHERE execution_id = ?",
            execIdStr);
        if (!logRows.isEmpty()) {
          String status = (String) logRows.get(0).get("status");
          if ("SUCCESS".equals(status) || "FAILURE".equals(status)) {
            result.put("status", status);
            result.put("output", logRows.get(0).get("output_result"));
            result.put("durationMs", logRows.get(0).get("duration_ms"));
            result.put("message", "执行完成");
            return result;
          }
        }
      }
      result.put("message", "执行超时未完成，请通过executionId查询结果");
    }

    return result;
  }

  private void recordInvokeLog(String apiCode, Long taskId, String executionId, Map<String, Object> payload, long startMs) {
    try {
      jdbcTemplate.update(
          "INSERT INTO open_api_invoke_log (api_code, task_id, execution_id, request_payload, duration_ms, created_time) VALUES (?,?,?,?,?,?)",
          apiCode, taskId, executionId,
          payload != null ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload) : "{}",
          System.currentTimeMillis() - startMs, LocalDateTime.now());
    } catch (Exception ignore) {}
  }

  private String generateApiCode() {
    return "OPEN_API_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  private OpenApiVO toVO(OpenApi entity) {
    OpenApiVO vo = new OpenApiVO();
    vo.setId(entity.getId());
    vo.setCode(entity.getApiCode());
    vo.setName(entity.getApiName());
    vo.setPath(entity.getApiPath());
    vo.setMethod(entity.getApiMethod());
    vo.setTaskId(entity.getTaskId());
    vo.setInputSchema(entity.getInputSchema());
    vo.setOutputSchema(entity.getOutputSchema());
    vo.setAuthType(entity.getAuthType());
    vo.setLimit(entity.getLimitCount());
    vo.setStatus(entity.getStatus() == 1 ? "启用" : "禁用");
    vo.setDesc(entity.getDescription());
    vo.setAppSecret(entity.getAppSecret());
    vo.setCreatedAt(entity.getCreatedTime());
    return vo;
  }
}
