package com.wonjaego.integration.zigzag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class ZigzagOpenApiClientImpl implements ZigzagOpenApiClient {

    private static final String ENDPOINT_PATH = "/1/graphql";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZigzagOpenApiClientImpl() {
        this.restClient = RestClient.builder()
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Override
    public JsonNode query(String accessKey, String secretKey, String baseUrl, String graphqlQuery) {
        String authorization = ZigzagAuthorizationHeaderFactory.create(accessKey, secretKey, graphqlQuery);
        String requestBody = objectMapper.createObjectNode().put("query", graphqlQuery).toString();

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(baseUrl + ENDPOINT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("지그재그 API 호출 실패", e);
            throw new ZigzagApiException("지그재그 API 호출에 실패했습니다: " + e.getMessage(), e);
        }

        JsonNode json = objectMapper.readTree(responseBody);
        if (json.has("errors")) {
            String errors = json.get("errors").toString();
            log.warn("지그재그 API 응답에 에러가 포함되어 있습니다: {}", errors);
            throw new ZigzagApiException("지그재그 API 에러 응답: " + errors);
        }
        return json.get("data");
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return factory;
    }
}
