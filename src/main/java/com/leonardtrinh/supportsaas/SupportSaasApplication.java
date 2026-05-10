package com.leonardtrinh.supportsaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportSaasApplication.class, args);
    }
}
