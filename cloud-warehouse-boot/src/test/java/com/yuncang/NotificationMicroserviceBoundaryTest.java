package com.yuncang;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.common.PageResult;
import com.yuncang.common.notification.NotificationView;
import com.yuncang.web.client.NotificationClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.discovery.enabled=false",
    "spring.rabbitmq.dynamic=false", "spring.rabbitmq.listener.simple.auto-startup=false",
    "app.notifications.enabled=false", "app.internal.token=boundary-test-token",
    "spring.datasource.url=jdbc:h2:mem:notification-boundary;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles({"test", "microservices"})
@AutoConfigureMockMvc
class NotificationMicroserviceBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext context;
    @MockitoBean NotificationClient client;

    private String login(String name, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("username", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("token").asText();
    }

    @Test
    void publicContractStillEnforcesRolesAndUsesRemoteReaderOnly() throws Exception {
        assertFalse(context.containsBean("notificationController"));
        assertFalse(context.containsBean("notificationConsumer"));
        assertTrue(context.containsBean("remoteNotificationController"));
        mvc.perform(get("/api/v1.1/notifications")).andExpect(status().isUnauthorized());
        String operator = login("operator", "YuncangDemo@2026");
        mvc.perform(get("/api/v1.1/notifications").header("Authorization", operator))
                .andExpect(status().isForbidden());
        String admin = login("admin", "123456");
        when(client.page("boundary-test-token", 2, 10)).thenReturn(
                new PageResult<NotificationView>(List.of(), 2, 10, 0));
        mvc.perform(get("/api/v1.1/notifications").header("Authorization", admin)
                .param("page", "2").param("size", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1.1/notifications").header("Authorization", admin)
                .param("page", "0")).andExpect(status().isBadRequest());
        verify(client, times(1)).page("boundary-test-token", 2, 10);
        verifyNoMoreInteractions(client);
        when(client.page("boundary-test-token", 1, 20)).thenThrow(mock(feign.FeignException.class));
        mvc.perform(get("/api/v1.1/notifications").header("Authorization", admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_SERVICE_UNAVAILABLE"));
    }
}
