package com.yuncang.notification.messaging;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@Profile("notification-mysql")
@ConditionalOnProperty(name = "app.notifications.consumer-enabled", havingValue = "true")
public class NotificationRabbitConfig {
    @Bean
    DirectExchange notificationExchange(@Value("${app.notifications.exchange:yuncang.stock-alert}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    DirectExchange notificationDeadExchange(@Value("${app.notifications.exchange:yuncang.stock-alert}") String name) {
        return new DirectExchange(name + ".dlx", true, false);
    }

    @Bean
    Queue notificationQueue(@Value("${app.notifications.queue:yuncang.stock-alert.notification}") String name,
            @Value("${app.notifications.exchange:yuncang.stock-alert}") String exchange) {
        return new Queue(name, true, false, false,
                Map.of("x-dead-letter-exchange", exchange + ".dlx",
                        "x-dead-letter-routing-key", "notification.failed"));
    }

    @Bean
    Queue notificationDeadQueue(@Value("${app.notifications.queue:yuncang.stock-alert.notification}") String name) {
        return new Queue(name + ".dlq", true);
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with("notification");
    }

    @Bean
    Binding notificationDeadBinding(Queue notificationDeadQueue, DirectExchange notificationDeadExchange) {
        return BindingBuilder.bind(notificationDeadQueue).to(notificationDeadExchange).with("notification.failed");
    }
}
