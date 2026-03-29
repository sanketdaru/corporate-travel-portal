package com.corporate.travel.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.corporate.travel.bff.config.BffProperties;

@SpringBootApplication
@EnableConfigurationProperties(BffProperties.class)
public class EmployeeBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeBffApplication.class, args);
    }
}
