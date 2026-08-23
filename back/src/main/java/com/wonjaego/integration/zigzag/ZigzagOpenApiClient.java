package com.wonjaego.integration.zigzag;

import tools.jackson.databind.JsonNode;

// Thin client for 지그재그(카카오스타일) Open API's GraphQL endpoint.
// https://zigzag.kr/_openapi/docs/request/ / https://zigzag.kr/_openapi/docs/authorization/
public interface ZigzagOpenApiClient {

    // Sends a GraphQL query and returns the "data" node. Throws ZigzagApiException (with
    // the raw error message/body from the response) on a network failure or a GraphQL-level
    // "errors" field — callers don't need to inspect the response shape themselves.
    JsonNode query(String accessKey, String secretKey, String baseUrl, String graphqlQuery);
}
