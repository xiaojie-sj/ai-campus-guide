package com.hbeu.campusguide;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan("com.hbeu.campusguide.mapper")
@ConfigurationPropertiesScan
public class CampusGuideApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusGuideApplication.class, args);
    }

}
