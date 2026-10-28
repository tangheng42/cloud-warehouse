package com.yuncang.web.service;

import java.util.Map;

import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.web.client.NotificationClient;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 远程通知查询服务
 * 保留学习接口，同时为微服务模式提供真实通知查询。
 */
@Service
public class RemoteNotificationQueryService {

    private static final Logger log =
            LoggerFactory.getLogger(RemoteNotificationQueryService.class);

    // 注入 OpenFeign 为接口创建的代理对象
    private final NotificationClient notificationClient;

    // 从配置中读取内部服务调用凭据
    private final String internalToken;

    public RemoteNotificationQueryService(
            NotificationClient notificationClient,
            @Value("${app.internal.token:}") String internalToken) {

        this.notificationClient = notificationClient;
        this.internalToken = internalToken;
    }

    /**
     * 通过 OpenFeign 查询通知微服务的演示数据
     */
    @CircuitBreaker(name = "notificationQuery")
    @RateLimiter(name = "notificationQueryLimiter")
    @Bulkhead(name = "notificationQueryBulkhead")
    public Map<String, Object> listLearningNotifications() {

        if (internalToken.isBlank()) {
            throw new IllegalStateException("未配置通知服务内部调用凭据");
        }

        try {
            // 发起 Feign 远程调用
            return notificationClient.getDemoNotifications(internalToken);

        } catch (RetryableException | FeignException.ServiceUnavailable e) {

            // 保留原始异常，方便开发人员排查
            log.warn("通知服务远程调用失败", e);

            // 转换为项目统一的业务异常
            throw new BusinessException(
                    ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE
            );
        }
    }

    @CircuitBreaker(name = "notificationQuery")
    @RateLimiter(name = "notificationQueryLimiter")
    @Bulkhead(name = "notificationQueryBulkhead")
    public com.yuncang.common.PageResult<com.yuncang.common.notification.NotificationView>
            listNotifications(int page, int size) {
        if (internalToken.isBlank()) {
            throw new BusinessException(ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE);
        }
        try {
            var result = notificationClient.page(internalToken, page, size);
            if (result == null || result.items() == null) {
                throw new BusinessException(ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE);
            }
            return result;
        } catch (FeignException e) {
            // 远程依赖失败必须显式报错，不能把故障伪装为空列表或回查旧库。
            log.warn("通知分页远程调用失败，HTTP status={}", e.status());
            throw new BusinessException(ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE);
        }
    }
}
