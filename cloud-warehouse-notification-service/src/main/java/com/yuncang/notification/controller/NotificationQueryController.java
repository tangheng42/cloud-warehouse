package com.yuncang.notification.controller;

import com.yuncang.notification.service.NotificationQueryService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("notification-mysql")
@RequestMapping("/internal/v1/notifications/page")
public class NotificationQueryController {
    private final NotificationQueryService service;
    private final String internalToken;

    public NotificationQueryController(NotificationQueryService service,
            @Value("${app.internal.token:}") String internalToken) {
        this.service = service;
        this.internalToken = internalToken;
        if (internalToken.isBlank()) {
            throw new IllegalStateException("APP_INTERNAL_TOKEN is required for database notification mode");
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        if (token == null || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (page < 1 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.list(page, size));
    }
}
