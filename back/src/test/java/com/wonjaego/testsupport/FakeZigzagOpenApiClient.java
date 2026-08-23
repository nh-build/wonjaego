package com.wonjaego.testsupport;

import com.wonjaego.integration.zigzag.ZigzagApiException;
import com.wonjaego.integration.zigzag.ZigzagOpenApiClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Test double standing in for a real Zigzag API round-trip. respondWith() takes exactly what
// ZigzagOpenApiClient.query() would normally return (the unwrapped GraphQL "data" node) —
// callers don't see the {"data": ...}/{"errors": ...} envelope, matching the real contract.
public class FakeZigzagOpenApiClient implements ZigzagOpenApiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonNode responseData = OBJECT_MAPPER.readTree("{}");
    private RuntimeException failure;

    public void respondWith(String dataJson) {
        this.responseData = OBJECT_MAPPER.readTree(dataJson);
        this.failure = null;
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    @Override
    public JsonNode query(String accessKey, String secretKey, String baseUrl, String graphqlQuery) {
        if (failure != null) {
            throw failure;
        }
        return responseData;
    }

    public static RuntimeException authError() {
        return new ZigzagApiException("지그재그 API 에러 응답: [{\"message\":\"invalid access key\"}]");
    }
}
