package com.cqie.datafactory.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.cqie.datafactory.configuration",
        "com.cqie.datafactory.common"
})
@MapperScan("com.cqie.datafactory.configuration.mapper")
public class ConfigurationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigurationApplication.class, args);
    }
}
