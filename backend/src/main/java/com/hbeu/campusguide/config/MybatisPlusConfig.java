package com.hbeu.campusguide.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/**
 * MyBatis-Plus pagination configuration.
 */
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Value("${spring.datasource.driver-class-name:org.h2.Driver}") String driverClassName
    ) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = driverClassName.contains("postgresql") ? DbType.POSTGRE_SQL : DbType.MYSQL;
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }
}
