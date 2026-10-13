package com.yuncang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class V1WorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String username, String password) throws Exception {
        JsonNode result = json.readTree(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + result.path("data").path("token").asText();
    }

    private JsonNode postJson(String path, String body, String auth, int expected) throws Exception {
        return json.readTree(mvc.perform(post(path).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode action(String path, String auth, int expected) throws Exception {
        return json.readTree(mvc.perform(post(path).header("Authorization", auth))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode query(String path, String auth) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void stock(long warehouse, long sku, String auth, int physical, int available, int locked) throws Exception {
        JsonNode data = query("/api/v1/stocks/warehouses/" + warehouse + "/skus/" + sku, auth).path("data");
        assertEquals(physical, data.path("physicalStock").asInt());
        assertEquals(available, data.path("availableStock").asInt());
        assertEquals(locked, data.path("lockedStock").asInt());
    }

    @Test
    void completeV1InventoryLifecycle() throws Exception {
        String admin = login("admin", "123456");
        String operator = login("operator", "YuncangDemo@2026");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long source = postJson("/api/v1/warehouses", "{\"code\":\"SRC-" + suffix
                + "\",\"name\":\"源仓\"}", admin, 201).path("data").path("id").asLong();
        long target = postJson("/api/v1/warehouses", "{\"code\":\"DST-" + suffix
                + "\",\"name\":\"目标仓\"}", admin, 201).path("data").path("id").asLong();
        long category = postJson("/api/v1/sku-categories", "{\"code\":\"CAT-" + suffix
                + "\",\"name\":\"测试分类\"}", admin, 201).path("data").path("id").asLong();
        long sku = postJson("/api/v1/skus", "{\"code\":\"SKU-" + suffix
                + "\",\"name\":\"测试商品\",\"categoryId\":" + category
                + ",\"unit\":\"件\",\"safetyStock\":8}", admin, 201).path("data").path("id").asLong();

        long inbound = postJson("/api/v1/inbounds", "{\"warehouseId\":" + source
                + ",\"items\":[{\"skuId\":" + sku + ",\"quantity\":10}]}", operator, 201)
                .path("data").path("id").asLong();
        assertEquals("ACTIVE_ORDER_EXISTS", action("/api/v1/warehouses/" + source + "/disable", admin, 409).path("code").asText());
        assertEquals("ACTIVE_ORDER_EXISTS", action("/api/v1/skus/" + sku + "/disable", admin, 409).path("code").asText());
        action("/api/v1/inbounds/" + inbound + "/confirm", operator, 200);
        stock(source, sku, admin, 10, 10, 0);

        long outbound = postJson("/api/v1/outbounds", "{\"warehouseId\":" + source
                + ",\"items\":[{\"skuId\":" + sku + ",\"quantity\":3}]}", operator, 201)
                .path("data").path("id").asLong();
        action("/api/v1/outbounds/" + outbound + "/reserve", operator, 200);
        stock(source, sku, admin, 10, 7, 3);
        assertEquals(1, query("/api/v1/stock-alerts?warehouseId=" + source + "&status=ACTIVE", admin)
                .path("data").path("total").asInt());
        action("/api/v1/outbounds/" + outbound + "/release", operator, 200);
        stock(source, sku, admin, 10, 10, 0);
        action("/api/v1/outbounds/" + outbound + "/reserve", operator, 200);
        action("/api/v1/outbounds/" + outbound + "/confirm", operator, 200);
        stock(source, sku, admin, 7, 7, 0);
        assertEquals("ORDER_STATE_CONFLICT", action("/api/v1/outbounds/" + outbound + "/confirm", operator, 409)
                .path("code").asText());

        long transfer = postJson("/api/v1/transfers", "{\"sourceWarehouseId\":" + source
                + ",\"targetWarehouseId\":" + target + ",\"items\":[{\"skuId\":" + sku
                + ",\"quantity\":4}]}", operator, 201).path("data").path("id").asLong();
        action("/api/v1/transfers/" + transfer + "/reserve", operator, 200);
        stock(source, sku, admin, 7, 3, 4);
        action("/api/v1/transfers/" + transfer + "/release", operator, 200);
        stock(source, sku, admin, 7, 7, 0);
        action("/api/v1/transfers/" + transfer + "/reserve", operator, 200);
        action("/api/v1/transfers/" + transfer + "/dispatch", operator, 200);
        stock(source, sku, admin, 3, 3, 0);
        assertEquals("ORDER_STATE_CONFLICT", action("/api/v1/transfers/" + transfer + "/cancel", operator, 409)
                .path("code").asText());
        action("/api/v1/transfers/" + transfer + "/receive", operator, 200);
        stock(target, sku, admin, 4, 4, 0);
        long cancelledTransfer = postJson("/api/v1/transfers", "{\"sourceWarehouseId\":" + source
                + ",\"targetWarehouseId\":" + target + ",\"items\":[{\"skuId\":" + sku
                + ",\"quantity\":1}]}", operator, 201).path("data").path("id").asLong();
        action("/api/v1/transfers/" + cancelledTransfer + "/reserve", operator, 200);
        action("/api/v1/transfers/" + cancelledTransfer + "/cancel", operator, 200);
        stock(source, sku, admin, 3, 3, 0);

        long stale = postJson("/api/v1/stocktakes", "{\"warehouseId\":" + source
                + ",\"items\":[{\"skuId\":" + sku + ",\"countedPhysical\":2}]}", operator, 201)
                .path("data").path("id").asLong();
        assertEquals("FORBIDDEN", action("/api/v1/stocktakes/" + stale + "/confirm", operator, 403)
                .path("code").asText());
        long temporary = postJson("/api/v1/outbounds", "{\"warehouseId\":" + source
                + ",\"items\":[{\"skuId\":" + sku + ",\"quantity\":1}]}", operator, 201)
                .path("data").path("id").asLong();
        action("/api/v1/outbounds/" + temporary + "/reserve", operator, 200);
        assertEquals("STOCK_SNAPSHOT_CHANGED", action("/api/v1/stocktakes/" + stale + "/confirm", admin, 409)
                .path("code").asText());
        long belowLocked = postJson("/api/v1/stocktakes", "{\"warehouseId\":" + source
                + ",\"items\":[{\"skuId\":" + sku + ",\"countedPhysical\":0}]}", operator, 201)
                .path("data").path("id").asLong();
        assertEquals("COUNT_BELOW_LOCKED", action("/api/v1/stocktakes/" + belowLocked + "/confirm", admin, 409)
                .path("code").asText());
        action("/api/v1/stocktakes/" + belowLocked + "/cancel", operator, 200);
        action("/api/v1/outbounds/" + temporary + "/cancel", operator, 200);
        action("/api/v1/stocktakes/" + stale + "/cancel", operator, 200);
        long stocktake = postJson("/api/v1/stocktakes", "{\"warehouseId\":" + source
                + ",\"items\":[{\"skuId\":" + sku + ",\"countedPhysical\":2}]}", operator, 201)
                .path("data").path("id").asLong();
        action("/api/v1/stocktakes/" + stocktake + "/confirm", admin, 200);
        stock(source, sku, admin, 2, 2, 0);
        assertEquals(1, query("/api/v1/stock-flows?bizType=STOCKTAKE&bizId=" + stocktake, admin)
                .path("data").path("total").asInt());
        assertTrue(query("/api/v1/stock-alerts?warehouseId=" + source + "&status=ACTIVE", admin)
                .path("data").path("total").asInt() >= 1);
        mvc.perform(put("/api/v1/skus/" + sku).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"测试商品\",\"categoryId\":" + category
                        + ",\"unit\":\"件\",\"safetyStock\":0}"))
                .andExpect(status().isOk());
        assertEquals(0, query("/api/v1/stock-alerts?skuId=" + sku + "&status=ACTIVE", admin)
                .path("data").path("total").asInt());
    }
}
