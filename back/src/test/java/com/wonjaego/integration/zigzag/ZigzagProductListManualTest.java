package com.wonjaego.integration.zigzag;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

// Hits the REAL 지그재그 Open API with real credentials — not a assertion-driven correctness
// test, but a manual 연동 검증 you run yourself and read the console output of. Skipped
// automatically (including in CI / a plain `./gradlew test` run) unless both
// ZIGZAG_ACCESS_KEY and ZIGZAG_SECRET_KEY are set in the environment.
@Slf4j
@EnabledIfEnvironmentVariable(named = "ZIGZAG_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ZIGZAG_SECRET_KEY", matches = ".+")
class ZigzagProductListManualTest {

    // No arguments — 지그재그 GraphQL 스키마 기준 product_list는 파라미터를 받지 않고
    // 판매자의 전체 상품을 반환한다 (https://zigzag.kr/_openapi/openapi.graphql).
    private static final String PRODUCT_LIST_QUERY = """
            query {
              product_list {
                item_list {
                  id
                  name
                  sales_status
                  display_status
                }
              }
            }
            """;

    @Test
    void 내_상품_목록을_불러와서_콘솔에_출력한다() {
        String accessKey = System.getenv("ZIGZAG_ACCESS_KEY");
        String secretKey = System.getenv("ZIGZAG_SECRET_KEY");
        String baseUrl = System.getenv().getOrDefault("ZIGZAG_BASE_URL", "https://openapi.zigzag.kr");

        ZigzagOpenApiClient client = new ZigzagOpenApiClientImpl();

        JsonNode data;
        try {
            data = client.query(accessKey, secretKey, baseUrl, PRODUCT_LIST_QUERY);
        } catch (ZigzagApiException e) {
            // The exception message already carries the raw API/HTTP error — log it as-is.
            log.error("지그재그 상품 목록 조회 실패: {}", e.getMessage());
            throw e;
        }

        JsonNode itemList = data.path("product_list").path("item_list");
        log.info("지그재그 내 상품 {}건 조회됨", itemList.size());
        itemList.forEach(item -> log.info(" - [{}] {} (판매상태: {}, 노출상태: {})",
                item.path("id").asString(),
                item.path("name").asString(),
                item.path("sales_status").asString(),
                item.path("display_status").asString()));

        assertThat(itemList.isArray()).isTrue();
    }
}
