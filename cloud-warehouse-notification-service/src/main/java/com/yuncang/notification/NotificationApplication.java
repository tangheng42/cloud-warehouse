package com.yuncang.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 云仓智配通知微服务启动类
 *
 * 当前只负责独立启动和注册 Nacos。
 * 暂时不迁移原有通知业务。
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                NotificationApplication.class, args);
    }
}