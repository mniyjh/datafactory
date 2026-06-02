package com.cqie.datafactory.executor.grpc;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.grpc.proto.ExecuteRequest;
import com.cqie.datafactory.executor.grpc.proto.ExecuteResponse;
import com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest;
import com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse;
import com.cqie.datafactory.executor.grpc.proto.PythonExecutorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcPythonClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcPythonClient.class);

    private final ManagedChannel channel;
    private final PythonExecutorGrpc.PythonExecutorBlockingStub stub;

    public GrpcPythonClient(
            @Value("${grpc.python.host:127.0.0.1}") String host,
            @Value("${grpc.python.port:50051}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = PythonExecutorGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, Object> execute(String scriptContent, String scriptType,
                                        Map<String, String> inputParams,
                                        int timeoutSeconds, String workDir) {
        return execute(scriptContent, scriptType, null, null, inputParams, timeoutSeconds, workDir);
    }

    /**
     * 带类名和方法名的执行（类方法调用模式）.
     * @param className 类名，null/空则默认 Script
     * @param methodName 方法名，null/空则默认 execute
     */
    public Map<String, Object> execute(String scriptContent, String scriptType,
                                        String className, String methodName,
                                        Map<String, String> inputParams,
                                        int timeoutSeconds, String workDir) {
        var builder = ExecuteRequest.newBuilder()
                .setScriptContent(scriptContent)
                .setScriptType(scriptType != null ? scriptType : "PYTHON")
                .putAllInputParams(inputParams != null ? inputParams : Map.of())
                .setTimeoutSeconds(timeoutSeconds)
                .setWorkDir(workDir != null ? workDir : "");
        if (className != null && !className.isBlank()) {
            builder.setClassName(className);
        }
        if (methodName != null && !methodName.isBlank()) {
            builder.setMethodName(methodName);
        }
        ExecuteRequest request = builder.build();

        try {
            ExecuteResponse resp = stub.withDeadlineAfter(timeoutSeconds + 10, TimeUnit.SECONDS)
                    .execute(request);

            Map<String, Object> result = new HashMap<>();
            result.put("exit_code", resp.getExitCode());
            result.put("stdout", resp.getStdout());
            result.put("stderr", resp.getStderr());
            result.put("result_json", resp.getResultJson());
            result.put("duration_ms", resp.getDurationMs());

            if (resp.getExitCode() != 0) {
                throw new BusinessException(
                        "Python脚本执行失败(exitCode=" + resp.getExitCode() + "): " + resp.getStderr());
            }

            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            log.error("gRPC调用失败: {}", e.getMessage());
            throw new BusinessException("Python执行器不可用: " + e.getMessage());
        }
    }

    public boolean isHealthy() {
        try {
            HealthCheckResponse resp = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                    .healthCheck(HealthCheckRequest.getDefaultInstance());
            return resp.getHealthy();
        } catch (Exception e) {
            return false;
        }
    }
}
