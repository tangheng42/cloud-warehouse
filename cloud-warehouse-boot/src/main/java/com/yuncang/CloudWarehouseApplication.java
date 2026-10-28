package com.yuncang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


@SpringBootApplication
@EnableFeignClients(basePackages = "com.yuncang.web.client")
public class CloudWarehouseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudWarehouseApplication.class, args);
    }
}
