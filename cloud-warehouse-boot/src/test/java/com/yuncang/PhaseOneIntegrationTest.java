package com.yuncang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseOneIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String username) throws Exception {
        String password = "admin".equals(username) ? "123456" : "YuncangDemo@2026";
        String result = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").exists())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(result).path("data").path("token").asText();
    }

    @Test
    void unknownStaticAndLegacyPathsReturn404() throws Exception {
        for (String path : new String[] {"/js/footer.js", "/api/blog/hot", "/api/shop-type/list"}) {
            mvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }
    }

    @Test
    void authCrudStockAndDocsFollowContract() throws Exception {
        mvc.perform(get("/api/v1/warehouses")).andExpect(status().isUnauthorized());
        String docsJson = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/warehouses']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = json.readTree(docsJson).path("paths");
        Set<String> actual = new HashSet<>();
        paths.fields().forEachRemaining(path -> path.getValue().fieldNames()
                .forEachRemaining(method -> actual.add(method.toUpperCase() + " " + path.getKey())));
        Set<String> expected = Set.of(
                "POST /api/v1/auth/login", "GET /api/v1/auth/me",
                "POST /api/v1/warehouses", "GET /api/v1/warehouses", "GET /api/v1/warehouses/{id}",
                "PUT /api/v1/warehouses/{id}", "POST /api/v1/warehouses/{id}/enable", "POST /api/v1/warehouses/{id}/disable",
                "POST /api/v1/sku-categories", "GET /api/v1/sku-categories", "GET /api/v1/sku-categories/{id}",
                "PUT /api/v1/sku-categories/{id}", "POST /api/v1/sku-categories/{id}/enable", "POST /api/v1/sku-categories/{id}/disable",
                "POST /api/v1/skus", "GET /api/v1/skus", "GET /api/v1/skus/{id}", "PUT /api/v1/skus/{id}",
                "POST /api/v1/skus/{id}/enable", "POST /api/v1/skus/{id}/disable",
                "GET /api/v1/stocks", "GET /api/v1/stocks/warehouses/{warehouseId}/skus/{skuId}",
                "POST /api/v1/inbounds", "GET /api/v1/inbounds", "GET /api/v1/inbounds/{id}",
                "POST /api/v1/inbounds/{id}/cancel", "POST /api/v1/inbounds/{id}/confirm",
                "POST /api/v1/outbounds", "GET /api/v1/outbounds", "GET /api/v1/outbounds/{id}",
                "POST /api/v1/outbounds/{id}/reserve", "POST /api/v1/outbounds/{id}/release",
                "POST /api/v1/outbounds/{id}/confirm", "POST /api/v1/outbounds/{id}/cancel",
                "POST /api/v1/transfers", "GET /api/v1/transfers", "GET /api/v1/transfers/{id}",
                "POST /api/v1/transfers/{id}/reserve", "POST /api/v1/transfers/{id}/release",
                "POST /api/v1/transfers/{id}/dispatch", "POST /api/v1/transfers/{id}/receive",
                "POST /api/v1/transfers/{id}/cancel",
                "POST /api/v1/stocktakes", "GET /api/v1/stocktakes", "GET /api/v1/stocktakes/{id}",
                "POST /api/v1/stocktakes/{id}/confirm", "POST /api/v1/stocktakes/{id}/cancel",
                "GET /api/v1/stock-flows", "GET /api/v1/stock-flows/{id}",
                "GET /api/v1/stock-alerts");
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
        mvc.perform(get("/openapi/yuncang-openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("/transfers/{id}/receive:")));
        String contractJson = mvc.perform(get("/openapi/yuncang-openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("云仓智配 V1.0 API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.operationId").value("postAuthLogin"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers/{id}/receive'].post.operationId").value("postTransfersIdReceive"))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(40, json.readTree(contractJson).path("paths").size());
        Set<String> contractOperations = new HashSet<>();
        json.readTree(contractJson).path("paths").fields().forEachRemaining(path ->
                path.getValue().fieldNames().forEachRemaining(method ->
                        contractOperations.add(method.toUpperCase() + " " + path.getKey())));
        org.junit.jupiter.api.Assertions.assertEquals(actual, contractOperations);
        org.junit.jupiter.api.Assertions.assertEquals(50, contractOperations.size());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[0].url").value("/openapi/yuncang-openapi.json"))
                .andExpect(jsonPath("$.urls[1].url").value("/v3/api-docs"));

        String admin = login("admin");
        String operator = login("operator");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));
        mvc.perform(post("/api/v1/warehouses").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());

        String warehouseJson = mvc.perform(post("/api/v1/warehouses").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"WH-TEST\",\"name\":\"测试仓\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        long warehouseId = json.readTree(warehouseJson).path("data").path("id").asLong();
        mvc.perform(post("/api/v1/warehouses").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"WH-TEST\",\"name\":\"重复\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        mvc.perform(put("/api/v1/warehouses/" + warehouseId).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"新名称\",\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/warehouses?page=1&size=10").header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/warehouses?size=101").header("Authorization", operator))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String categoryJson = mvc.perform(post("/api/v1/sku-categories").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"CAT-TEST\",\"name\":\"测试分类\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long categoryId = json.readTree(categoryJson).path("data").path("id").asLong();
        String skuJson = mvc.perform(post("/api/v1/skus").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"SKU-TEST\",\"name\":\"测试SKU\",\"categoryId\":" + categoryId + ",\"unit\":\"件\",\"safetyStock\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long skuId = json.readTree(skuJson).path("data").path("id").asLong();
        mvc.perform(post("/api/v1/sku-categories/" + categoryId + "/disable").header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTIVE_SKU_EXISTS"));
        mvc.perform(get("/api/v1/stocks/warehouses/" + warehouseId + "/skus/" + skuId)
                .header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.physicalStock").value(0));
        mvc.perform(get("/api/v1/stocks").header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
}
