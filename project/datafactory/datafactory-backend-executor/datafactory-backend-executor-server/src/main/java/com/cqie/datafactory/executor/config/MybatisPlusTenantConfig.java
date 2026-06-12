package com.cqie.datafactory.executor.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.cqie.datafactory.common.context.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusTenantConfig {

    /** 不需要租户隔离的表 (系统表) */
    private static final String[] IGNORE_TABLES = {
            "sys_user", "sys_role", "sys_permission",
            "sys_user_role", "sys_role_permission", "sys_audit_log",
            "sys_tenant", "sys_user_tenant"
    };

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.get();
                if (tenantId == null) {
                    // 返回 0 保证查不到任何数据，而不是查出所有租户的数据
                    return new LongValue(0);
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                for (String ignore : IGNORE_TABLES) {
                    if (ignore.equalsIgnoreCase(tableName)) {
                        return true;
                    }
                }
                return false;
            }
        });

        interceptor.addInnerInterceptor(tenantInterceptor);
        return interceptor;
    }

    @Bean
    public SqlSessionTemplate batchSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory, ExecutorType.BATCH);
    }
}
