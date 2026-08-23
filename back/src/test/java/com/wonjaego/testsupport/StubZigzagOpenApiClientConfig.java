package com.wonjaego.testsupport;

import com.wonjaego.integration.zigzag.ZigzagOpenApiClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class StubZigzagOpenApiClientConfig {

    @Bean
    @Primary
    public ZigzagOpenApiClient zigzagOpenApiClient() {
        return new FakeZigzagOpenApiClient();
    }
}
