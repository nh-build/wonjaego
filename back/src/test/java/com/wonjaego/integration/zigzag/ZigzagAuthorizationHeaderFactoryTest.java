package com.wonjaego.integration.zigzag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZigzagAuthorizationHeaderFactoryTest {

    // Expected signature independently computed (HMAC-SHA1, hex) for:
    //   secretKey="test-secret-key", message="1700000000000.query { hello { message } } "
    // — a fixed fixture, not a value read off this class's own implementation.
    private static final String EXPECTED_SIGNATURE = "5cd970fd9af286a2cd182cd7c84800c7b0a9a129";

    @Test
    void 헤더_형식과_서명이_지그재그_공식_예제와_일치한다() {
        String header = ZigzagAuthorizationHeaderFactory.create(
                "test-access-key", "test-secret-key", "query { hello { message } }", 1700000000000L);

        assertThat(header).isEqualTo(
                "CEA algorithm=HmacSHA256, access-key=test-access-key, signed-date=1700000000000, signature="
                        + EXPECTED_SIGNATURE);
    }

    @Test
    void 헤더에는_HmacSHA256이라_적히지만_서명은_HmacSHA1로_계산된다() {
        String header = ZigzagAuthorizationHeaderFactory.create(
                "test-access-key", "test-secret-key", "query { hello { message } }", 1700000000000L);

        assertThat(header).contains("algorithm=HmacSHA256");
        assertThat(header).endsWith("signature=" + EXPECTED_SIGNATURE);
    }

    @Test
    void 쿼리의_연속된_공백은_하나로_축약된_뒤_서명된다() {
        String collapsedWhitespaceQuery = "query { hello { message } }";
        String multilineQuery = "query {\n  hello {\n    message\n  }\n}";

        String headerFromCollapsed = ZigzagAuthorizationHeaderFactory.create(
                "test-access-key", "test-secret-key", collapsedWhitespaceQuery, 1700000000000L);
        String headerFromMultiline = ZigzagAuthorizationHeaderFactory.create(
                "test-access-key", "test-secret-key", multilineQuery, 1700000000000L);

        assertThat(headerFromMultiline).isEqualTo(headerFromCollapsed);
    }

    @Test
    void signed_date는_호출_시점의_현재_시각_밀리초다() {
        long before = System.currentTimeMillis();
        String header = ZigzagAuthorizationHeaderFactory.create("access", "secret", "query { hello { message } }");
        long after = System.currentTimeMillis();

        String signedDate = header.replaceAll(".*signed-date=(\\d+),.*", "$1");
        long parsed = Long.parseLong(signedDate);
        assertThat(parsed).isBetween(before, after);
    }
}
