package com.yuncang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import java.util.UUID;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SimpleBusinessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired SqlSessionTemplate sqlSession;

    private String token;
    private long source;
    private long target;
    private long sku;
    private long otherSku;

    @BeforeEach
    void fixtures() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        db.update("INSERT INTO warehouse (code, name, enabled) VALUES (?, '源仓', TRUE)", "SRC-" + suffix);
        db.update("INSERT INTO warehouse (code, name, enabled) VALUES (?, '目标仓', TRUE)", "DST-" + suffix);
        source = db.queryForObject("SELECT id FROM warehouse WHERE code = ?", Long.class, "SRC-" + suffix);
        target = db.queryForObject("SELECT id FROM warehouse WHERE code = ?", Long.class, "DST-" + suffix);
        db.update("INSERT INTO sku_category (code, name, enabled) VALUES (?, '测试分类', TRUE)", "CAT-" + suffix);
        long category = db.queryForObject("SELECT id FROM sku_category WHERE code = ?", Long.class, "CAT-" + suffix);
        db.update("INSERT INTO sku (code, name, category_id, unit, safety_stock, enabled) VALUES (?, '测试SKU', ?, '件', 10, TRUE)",
                "SKU-A-" + suffix, category);
        db.update("INSERT INTO sku (code, name, category_id, unit, safety_stock, enabled) VALUES (?, '测试SKU2', ?, '件', 10, TRUE)",
                "SKU-B-" + suffix, category);
        sku = db.queryForObject("SELECT id FROM sku WHERE code = ?", Long.class, "SKU-A-" + suffix);
        otherSku = db.queryForObject("SELECT id FROM sku WHERE code = ?", Long.class, "SKU-B-" + suffix);
        db.update("INSERT INTO warehouse_stock (warehouse_id, sku_id, physical_stock, available_stock, locked_stock) VALUES (?, ?, 10, 10, 0)",
                source, sku);
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + json.readTree(body).path("data").path("token").asText();
    }

    private JsonNode create(String path, String body) throws Exception {
        String response = mvc.perform(post(path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data");
    }

    private void expectError(ResultActions result, int httpStatus, String code, String message) throws Exception {
        result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void errorCodesKeepHttpResponseContract() throws Exception {
        assertEquals(409, new BusinessException(ErrorCode.INSUFFICIENT_AVAILABLE_STOCK).getStatus());
        assertEquals("可用库存不足",
                new BusinessException(ErrorCode.INSUFFICIENT_AVAILABLE_STOCK).getMessage());

        expectError(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}")),
                401, "INVALID_CREDENTIALS", "用户名或密码错误");
        expectError(mvc.perform(get("/api/v1/inbounds/999999").header("Authorization", token)),
                404, "NOT_FOUND", "入库单不存在");

        String body = "{\"warehouseId\":" + source + ",\"items\":[{\"skuId\":" + sku + ",\"quantity\":1}]}";
        String duplicate = "{\"warehouseId\":" + source + ",\"items\":[{\"skuId\":" + sku
                + ",\"quantity\":1},{\"skuId\":" + sku + ",\"quantity\":1}]}";
        expectError(mvc.perform(post("/api/v1/inbounds").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(duplicate)),
                409, "DUPLICATE_SKU_LINE", "同一单据不能重复添加相同 SKU");

        long inboundId = create("/api/v1/inbounds", body).path("id").asLong();
        mvc.perform(post("/api/v1/inbounds/" + inboundId + "/cancel").header("Authorization", token))
                .andExpect(status().isOk());
        expectError(mvc.perform(post("/api/v1/inbounds/" + inboundId + "/cancel")
                .header("Authorization", token)),
                409, "ORDER_STATE_CONFLICT", "入库单当前状态不可取消");

        long outboundId = create("/api/v1/outbounds", "{\"warehouseId\":" + source
                + ",\"items\":[{\"skuId\":" + sku + ",\"quantity\":11}]}").path("id").asLong();
        expectError(mvc.perform(post("/api/v1/outbounds/" + outboundId + "/reserve")
                .header("Authorization", token)),
                409, "INSUFFICIENT_AVAILABLE_STOCK", "可用库存不足");

        db.update("UPDATE warehouse SET enabled = FALSE WHERE id = ?", source);
        sqlSession.clearCache();
        expectError(mvc.perform(post("/api/v1/inbounds").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)),
                409, "RESOURCE_DISABLED", "仓库已停用");

        db.update("UPDATE app_user SET enabled = FALSE WHERE username = 'operator'");
        sqlSession.clearCache();
        expectError(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"operator\",\"password\":\"YuncangDemo@2026\"}")),
                403, "USER_DISABLED", "用户已停用");
    }

    @Test
    void inboundCreateQueryValidationAndCancel() throws Exception {
        String body = "{\"warehouseId\":" + source + ",\"items\":[{\"skuId\":" + sku + ",\"quantity\":2}]}";
        JsonNode created = create("/api/v1/inbounds", body);
        long id = created.path("id").asLong();
        assertEquals("PENDING", created.path("status").asText());
        assertEquals(1, created.path("createdBy").asLong());
        assertEquals(1, created.path("items").size());
        mvc.perform(get("/api/v1/inbounds/" + id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(2));
        mvc.perform(get("/api/v1/inbounds").header("Authorization", token)
                .param("warehouseId", Long.toString(source)).param("status", "PENDING")
                .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].items[0].skuId").value(sku));
        String duplicate = "{\"warehouseId\":" + source + ",\"items\":[{\"skuId\":" + sku
                + ",\"quantity\":1},{\"skuId\":" + sku + ",\"quantity\":2}]}";
        mvc.perform(post("/api/v1/inbounds").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SKU_LINE"));
        db.update("UPDATE warehouse SET enabled = FALSE WHERE id = ?", source);
        sqlSession.clearCache();
        mvc.perform(post("/api/v1/inbounds").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_DISABLED"));
        db.update("UPDATE warehouse SET enabled = TRUE WHERE id = ?", source);
        db.update("UPDATE sku SET enabled = FALSE WHERE id = ?", sku);
        sqlSession.clearCache();
        mvc.perform(post("/api/v1/inbounds").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_DISABLED"));
        mvc.perform(post("/api/v1/inbounds/" + id + "/cancel").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledBy").value(1))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty());
        mvc.perform(post("/api/v1/inbounds/" + id + "/cancel").header("Authorization", token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
        assertEquals(10L, db.queryForObject("SELECT physical_stock FROM warehouse_stock WHERE warehouse_id = ? AND sku_id = ?",
                Long.class, source, sku));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM stock_flow", Long.class));
    }

    @Test
    void outboundAndTransferCreateWithoutInventoryMovement() throws Exception {
        String item = "[{\"skuId\":" + sku + ",\"quantity\":100}]";
        JsonNode outbound = create("/api/v1/outbounds", "{\"warehouseId\":" + source + ",\"items\":" + item + "}");
        assertEquals("CREATED", outbound.path("status").asText());
        assertEquals(1, outbound.path("createdBy").asLong());
        mvc.perform(get("/api/v1/outbounds/" + outbound.path("id").asLong()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(100));
        mvc.perform(get("/api/v1/outbounds").header("Authorization", token).param("status", "CREATED")
                .param("warehouseId", Long.toString(source)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        String transferBody = "{\"sourceWarehouseId\":" + source + ",\"targetWarehouseId\":" + target
                + ",\"items\":" + item + "}";
        JsonNode transfer = create("/api/v1/transfers", transferBody);
        assertEquals("CREATED", transfer.path("status").asText());
        assertEquals(1, transfer.path("createdBy").asLong());
        mvc.perform(get("/api/v1/transfers/" + transfer.path("id").asLong()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetWarehouseId").value(target));
        mvc.perform(get("/api/v1/transfers").header("Authorization", token)
                .param("sourceWarehouseId", Long.toString(source)).param("targetWarehouseId", Long.toString(target)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(post("/api/v1/transfers").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sourceWarehouseId\":" + source
                        + ",\"targetWarehouseId\":" + source + ",\"items\":" + item + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAME_WAREHOUSE"));
        String duplicateItems = "[{\"skuId\":" + sku + ",\"quantity\":1},{\"skuId\":" + sku + ",\"quantity\":1}]";
        mvc.perform(post("/api/v1/transfers").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sourceWarehouseId\":" + source
                        + ",\"targetWarehouseId\":" + target + ",\"items\":" + duplicateItems + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SKU_LINE"));
        assertEquals(10L, db.queryForObject("SELECT available_stock FROM warehouse_stock WHERE warehouse_id = ? AND sku_id = ?",
                Long.class, source, sku));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM stock_flow", Long.class));
    }

    @Test
    void stocktakeFlowAndAlertQueries() throws Exception {
        db.update("INSERT INTO stocktake_order (order_no, warehouse_id, status, created_by) VALUES (?, ?, 'DRAFT', 1)",
                "ST-" + UUID.randomUUID(), source);
        long draft = db.queryForObject("SELECT MAX(id) FROM stocktake_order", Long.class);
        db.update("INSERT INTO stocktake_order_item (order_id, sku_id, snapshot_physical, snapshot_available, snapshot_locked, counted_physical) VALUES (?, ?, 10, 10, 0, 9)",
                draft, sku);
        db.update("INSERT INTO stocktake_order (order_no, warehouse_id, status, created_by) VALUES (?, ?, 'COMPLETED', 1)",
                "ST-" + UUID.randomUUID(), source);
        long completed = db.queryForObject("SELECT MAX(id) FROM stocktake_order", Long.class);
        db.update("INSERT INTO stocktake_order_item (order_id, sku_id, snapshot_physical, snapshot_available, snapshot_locked, counted_physical) VALUES (?, ?, 10, 10, 0, 10)",
                completed, sku);
        mvc.perform(get("/api/v1/stocktakes/" + draft).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].countedPhysical").value(9));
        mvc.perform(get("/api/v1/stocktakes").header("Authorization", token)
                .param("warehouseId", Long.toString(source)).param("status", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(post("/api/v1/stocktakes/" + completed + "/cancel").header("Authorization", token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
        mvc.perform(post("/api/v1/stocktakes/" + draft + "/cancel").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelledBy").value(1))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/stocktakes/" + draft + "/cancel").header("Authorization", token))
                .andExpect(status().isConflict());
        db.update("INSERT INTO stock_flow (warehouse_id, sku_id, biz_type, biz_id, flow_type, delta_physical, delta_available, delta_locked, before_physical, before_available, before_locked, after_physical, after_available, after_locked, operator_id) VALUES (?, ?, 'INBOUND', ?, 'INBOUND', 1, 1, 0, 10, 10, 0, 11, 11, 0, 1)",
                source, sku, draft);
        long flow = db.queryForObject("SELECT MAX(id) FROM stock_flow", Long.class);
        mvc.perform(get("/api/v1/stock-flows/" + flow).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deltaPhysical").value(1));
        mvc.perform(get("/api/v1/stock-flows").header("Authorization", token)
                .param("warehouseId", Long.toString(source)).param("skuId", Long.toString(sku))
                .param("bizType", "INBOUND").param("bizId", Long.toString(draft))
                .param("flowType", "INBOUND").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/stock-flows/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
        db.update("INSERT INTO stock_alert (warehouse_id, sku_id, available_stock, safety_stock, status, triggered_at) VALUES (?, ?, 5, 10, 'ACTIVE', CURRENT_TIMESTAMP)",
                source, sku);
        db.update("INSERT INTO stock_alert (warehouse_id, sku_id, available_stock, safety_stock, status, triggered_at, resolved_at) VALUES (?, ?, 10, 10, 'RESOLVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                target, otherSku);
        mvc.perform(get("/api/v1/stock-alerts").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
        mvc.perform(get("/api/v1/stock-alerts").header("Authorization", token)
                .param("status", "RESOLVED").param("warehouseId", Long.toString(target))
                .param("skuId", Long.toString(otherSku)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/stock-alerts").header("Authorization", token).param("status", "BAD"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stocktakes/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM warehouse_stock", Long.class) >= 1);
    }
}
