package com.cqie.datafactory.executor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.mapper.ExecutionLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "com.cqie.datafactory.executor",
        "com.cqie.datafactory.configuration",
        "com.cqie.datafactory.common"
})
@MapperScan({"com.cqie.datafactory.executor.mapper", "com.cqie.datafactory.executor.schedule.mapper", "com.cqie.datafactory.configuration.mapper"})
@EnableFeignClients(basePackages = "com.cqie.datafactory.executor.feign")
@EnableScheduling
public class ExecutorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExecutorApplication.class, args);
    }

    @Bean
    CommandLineRunner recoverStaleExecutions(ExecutionLogMapper executionLogMapper) {
        return args -> {
            var wrapper = new LambdaUpdateWrapper<ExecutionLog>()
                    .eq(ExecutionLog::getStatus, "RUNNING")
                    .set(ExecutionLog::getStatus, "FAILURE")
                    .set(ExecutionLog::getErrorMessage, "服务重启导致任务中断")
                    .set(ExecutionLog::getEndTime, LocalDateTime.now());
            executionLogMapper.update(null, wrapper);
            log.info("已清理僵尸执行记录");
        };
    }
}
