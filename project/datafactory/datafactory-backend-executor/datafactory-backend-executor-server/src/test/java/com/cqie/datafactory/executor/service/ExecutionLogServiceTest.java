package com.cqie.datafactory.executor.service;

import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.entity.NodeExecutionLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionLogServiceTest {

    @Test
    void shouldCreateValidExecutionLog() {
        ExecutionLog log = new ExecutionLog();
        log.setExecutionId("exec_test_001");
        log.setTaskId(1L);
        log.setTaskName("Test Task");
        log.setTaskVersion("1.0.0");
        log.setEnvironment("TEST");
        log.setStatus("RUNNING");
        log.setTriggerType("MANUAL");
        log.setStartTime(LocalDateTime.now());

        assertEquals("exec_test_001", log.getExecutionId());
        assertEquals("RUNNING", log.getStatus());
        assertNotNull(log.getStartTime());
    }

    @Test
    void shouldCreateValidNodeExecutionLog() {
        NodeExecutionLog nodeLog = new NodeExecutionLog();
        nodeLog.setExecutionId("exec_test_001");
        nodeLog.setNodeId("node1");
        nodeLog.setNodeName("DB Query");
        nodeLog.setNodeType("DB");
        nodeLog.setStatus("SUCCESS");
        nodeLog.setRetryCount(0);
        nodeLog.setDurationMs(150L);

        assertEquals("SUCCESS", nodeLog.getStatus());
        assertEquals(0, nodeLog.getRetryCount());
        assertEquals(150L, nodeLog.getDurationMs());
    }

    @Test
    void shouldHandleFailureStatus() {
        ExecutionLog log = new ExecutionLog();
        log.setExecutionId("exec_test_002");
        log.setStatus("FAILURE");
        log.setErrorMessage("Connection timeout");

        assertEquals("FAILURE", log.getStatus());
        assertEquals("Connection timeout", log.getErrorMessage());
    }
}
