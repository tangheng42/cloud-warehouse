package com.yuncang.web.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 将唯一 OpenAPI YAML 契约转换为 Knife4j 可调试的 JSON。 */
@RestController
@Hidden
public class OpenApiContractController {
    private final JsonNode contract;

    public OpenApiContractController() throws IOException {
        var resource = new ClassPathResource("static/openapi/yuncang-openapi.yaml");
        try (var input = resource.getInputStream()) {
            ObjectNode document = (ObjectNode) new YAMLMapper().readTree(input);
            String basePath = document.path("servers").path(0).path("url").asText();
            if (!basePath.startsWith("/") || basePath.equals("/")) {
                throw new IOException("OpenAPI servers[0].url 必须是非根相对路径");
            }
            String prefix = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
            ObjectNode uiPaths = document.objectNode();
            document.path("paths").fields().forEachRemaining(entry ->
                    uiPaths.set(prefix + entry.getKey(), entry.getValue()));
            document.set("paths", uiPaths);
            document.putArray("servers").addObject().put("url", "/");
            contract = document;
        }
    }

    @GetMapping(value = "/openapi/yuncang-openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getContract() {
        return contract;
    }
}
