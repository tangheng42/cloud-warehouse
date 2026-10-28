package com.yuncang;

import com.yuncang.web.controller.NotificationController;
import com.yuncang.web.notification.NotificationConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationProfileIsolationTest {
    @Test
    void enablingWarehouseOutboxCannotEnableLegacyNotificationReaderOrConsumer() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=microservices", "app.notifications.enabled=true")
                .withUserConfiguration(NotificationController.class, NotificationConsumer.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationController.class);
                    assertThat(context).doesNotHaveBean(NotificationConsumer.class);
                });
    }
}
