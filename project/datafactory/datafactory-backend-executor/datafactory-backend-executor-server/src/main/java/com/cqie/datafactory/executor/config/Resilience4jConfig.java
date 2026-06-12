package com.cqie.datafactory.executor.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class Resilience4jConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)           // 50% 失败率打开断路器
                .slowCallRateThreshold(50)          // 50% 慢调用率
                .slowCallDurationThreshold(Duration.ofSeconds(10))  // 超过10秒视为慢调用
                .waitDurationInOpenState(Duration.ofSeconds(30))    // 打开后30秒尝试半开
                .permittedNumberOfCallsInHalfOpenState(3)           // 半开状态允许3次探测
                .slidingWindowSize(10)                              // 滑动窗口大小
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .minimumNumberOfCalls(5)                            // 最少5次调用才计算
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        // 预注册各插件断路器
        registry.circuitBreaker("dbPlugin");
        registry.circuitBreaker("apiPlugin");
        registry.circuitBreaker("scriptPlugin");
        registry.circuitBreaker("grpcPython");

        log.info("CircuitBreaker registry initialized with: dbPlugin, apiPlugin, scriptPlugin, grpcPython");
        return registry;
    }
}
