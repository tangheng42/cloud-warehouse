package com.yuncang.web.service;

import com.yuncang.common.BusinessException;
import com.yuncang.common.PageResult;
import com.yuncang.web.client.NotificationClient;
import feign.FeignException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteNotificationQueryServiceTest {
    @Test
    void forwardsPaginationAndCredential() {
        var client = mock(NotificationClient.class);
        var expected = new PageResult<com.yuncang.common.notification.NotificationView>(List.of(), 2, 10, 0);
        when(client.page("secret", 2, 10)).thenReturn(expected);
        var service = new RemoteNotificationQueryService(client, "secret");
        assertSame(expected, service.listNotifications(2, 10));
        verify(client).page("secret", 2, 10);
    }

    @Test
    void dependencyFailureDoesNotBecomeAnEmptySuccess() {
        var client = mock(NotificationClient.class);
        when(client.page("secret", 1, 20)).thenThrow(mock(FeignException.class));
        assertThrows(BusinessException.class,
                () -> new RemoteNotificationQueryService(client, "secret").listNotifications(1, 20));
        assertThrows(BusinessException.class,
                () -> new RemoteNotificationQueryService(client, "").listNotifications(1, 20));
    }
}
