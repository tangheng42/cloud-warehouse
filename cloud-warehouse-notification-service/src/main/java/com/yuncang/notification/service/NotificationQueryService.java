package com.yuncang.notification.service;

import com.yuncang.common.PageResult;
import com.yuncang.common.notification.NotificationView;
import com.yuncang.notification.persistence.NotificationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("notification-mysql")
public class NotificationQueryService {
    private final NotificationMapper mapper;
    public NotificationQueryService(NotificationMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public PageResult<NotificationView> list(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 1 and size between 1 and 100");
        }
        long total = mapper.count();
        return new PageResult<>(mapper.page(size, ((long) page - 1) * size), page, size, total);
    }
}
