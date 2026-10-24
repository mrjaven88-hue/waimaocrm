package com.ftcrm;

import com.ftcrm.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class FtcrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtcrmApplication.class, args);
    }
}
