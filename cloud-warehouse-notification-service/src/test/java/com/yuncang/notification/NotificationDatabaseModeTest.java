package com.yuncang.notification;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads real application configuration without registering with Nacos or connecting to a DB. */
class NotificationDatabaseModeTest {
    @Test
    void defaultModeStillStartsWithoutDatabase() {
        try (var context = new SpringApplicationBuilder(NotificationApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.profiles.active=baseline-no-database",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.discovery.enabled=false")) {
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(context.getBeansOfType(SqlSessionFactory.class).isEmpty());
            assertTrue(context.containsBean("internalNotificationController"));
        }
    }
}
