package com.lizhe.dev.tech;

import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.lizhe.dev.tech", "com.lizhe.trigger"},
        exclude = {RedissonAutoConfigurationV2.class})
@Configurable

public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

}
