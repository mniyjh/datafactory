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
