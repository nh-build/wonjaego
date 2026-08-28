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

    // product_list는 인자를 하나도 받지 않는다(페이지네이션도, 날짜/상태/카테고리 같은 필터도
    // 없음 — 공식 스키마 https://zigzag.kr/_openapi/openapi.graphql 및 introspection 시도로
    // 2026-08-23에 확인). 응답도 항상 {data: {product_list: {item_list: [...]}}} 뿐이라
    // total_count/cursor 등 다음 페이지를 가리키는 정보가 전혀 없고, 실측 결과 정확히 100건에서
    // 잘렸다 — 문서화되지 않은 서버 측 상한으로 보인다(지그재그 파트너센터에 문의 중). 즉 이
    // 쿼리로는 셀러 상품이 100개를 넘어도 100개까지만 가져올 수 있다 — 아래 테스트가 정확히
    // 100건을 출력하면 이 상한에 걸렸다는 뜻이지, 그 계정의 전체 상품 수라는 보장이 없다.
    private static final int KNOWN_PRODUCT_LIST_CAP = 100;

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
        if (itemList.size() == KNOWN_PRODUCT_LIST_CAP) {
            log.warn("정확히 {}건 조회됨 — product_list의 (문서화되지 않은) 상한에 걸렸을 가능성이 "
                    + "높습니다. 이 계정의 실제 전체 상품 수는 이보다 많을 수 있습니다.", KNOWN_PRODUCT_LIST_CAP);
        }
        itemList.forEach(item -> log.info(" - [{}] {} (판매상태: {}, 노출상태: {})",
                item.path("id").asString(),
                item.path("name").asString(),
                item.path("sales_status").asString(),
                item.path("display_status").asString()));

        assertThat(itemList.isArray()).isTrue();
    }
}
