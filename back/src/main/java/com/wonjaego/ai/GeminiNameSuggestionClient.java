package com.wonjaego.ai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Google Gemini (Interactions API, https://ai.google.dev/api/interactions-api) — vision-capable
// replacement for the old text-only Anthropic client. Sends a resized/compressed product photo
// (resize happens client-side, see product-form.html) plus a category-targeting instruction, and
// forces the response into a small JSON schema so parsing never depends on free-text formatting.
@Slf4j
@Component
public class GeminiNameSuggestionClient implements NameSuggestionClient {

    private static final String MODEL = "gemini-3.8-flash";
    private static final int MIN_CANDIDATES = 2;
    private static final int MAX_CANDIDATES = 3;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiNameSuggestionClient(ObjectMapper objectMapper,
                                       @Value("${wonjaego.ai.gemini.api-key}") String apiKey) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Override
    public List<String> suggest(byte[] photoBytes, String mimeType, String category) {
        ObjectNode requestBody = buildRequestBody(photoBytes, mimeType, category);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1beta/interactions")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Gemini API 호출 실패. category={}", category, e);
            throw new NameSuggestionFailedException("Gemini API 호출에 실패했습니다.", e);
        }

        return parse(response, category);
    }

    private ObjectNode buildRequestBody(byte[] photoBytes, String mimeType, String category) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", MODEL);

        ArrayNode input = root.putArray("input");
        ObjectNode textPart = input.addObject();
        textPart.put("type", "text");
        textPart.put("text", buildPrompt(category));
        ObjectNode imagePart = input.addObject();
        imagePart.put("type", "image");
        imagePart.put("data", Base64.getEncoder().encodeToString(photoBytes));
        imagePart.put("mime_type", mimeType);

        ObjectNode responseFormat = root.putObject("response_format");
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        ObjectNode schema = responseFormat.putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode candidatesSchema = properties.putObject("candidates");
        candidatesSchema.put("type", "array");
        candidatesSchema.putObject("items").put("type", "string");
        candidatesSchema.put("minItems", MIN_CANDIDATES);
        candidatesSchema.put("maxItems", MAX_CANDIDATES);
        schema.putArray("required").add("candidates");

        return root;
    }

    // 카테고리가 대상 지정에 그대로 쓰인다 — "투피스"는 세트 전체, "상의"는 상의만, 직접입력
    // 텍스트는 그 표현 그대로, 미지정이면 사진에 있는 상품 전체를 대상으로 짓게 한다.
    private String buildPrompt(String category) {
        String trimmed = category == null ? "" : category.trim();
        String targeting;
        if (trimmed.isEmpty()) {
            targeting = "사진에 있는 상품 전체를 대상으로 상품명을 지어줘.";
        } else {
            targeting = switch (trimmed) {
                case "상의" -> "사진에서 상의(윗옷)만을 대상으로 상품명을 지어줘.";
                case "하의" -> "사진에서 하의(아래옷)만을 대상으로 상품명을 지어줘.";
                case "원피스" -> "사진 속 원피스 한 벌 전체를 대상으로 상품명을 지어줘.";
                case "투피스" -> "사진 속 투피스(세트) 전체를 아우르는 상품명을 지어줘.";
                default -> "사진에서 '" + trimmed + "'에 해당하는 상품을 대상으로 상품명을 지어줘.";
            };
        }
        return "이 사진은 온라인 쇼핑몰에 등록할 상품 사진이야. " + targeting
                + " 자연스러운 한국어 상품명 후보를 " + MIN_CANDIDATES + "~" + MAX_CANDIDATES
                + "개 만들어줘. 각 이름은 20자 이내여야 해.";
    }

    private List<String> parse(JsonNode response, String category) {
        String rawText = extractOutputText(response);
        if (rawText == null || rawText.isBlank()) {
            log.warn("Gemini API로부터 빈 응답을 받았습니다. category={}", category);
            throw new NameSuggestionFailedException("Gemini API로부터 빈 응답을 받았습니다.");
        }

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(rawText);
        } catch (RuntimeException e) {
            log.warn("Gemini API 응답을 파싱할 수 없습니다. category={}, raw={}", category, rawText, e);
            throw new NameSuggestionFailedException("Gemini API 응답을 파싱할 수 없습니다.", e);
        }

        JsonNode candidatesNode = parsed.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
            log.warn("Gemini API 응답에 상품명 후보가 없습니다. category={}, raw={}", category, rawText);
            throw new NameSuggestionFailedException("Gemini API 응답에 상품명 후보가 없습니다.");
        }

        List<String> candidates = new ArrayList<>();
        for (JsonNode candidate : candidatesNode) {
            String name = candidate.asText("").trim();
            if (!name.isEmpty()) {
                candidates.add(name);
            }
        }
        if (candidates.isEmpty()) {
            log.warn("Gemini API 응답의 상품명 후보가 모두 비어 있습니다. category={}", category);
            throw new NameSuggestionFailedException("Gemini API 응답의 상품명 후보가 비어 있습니다.");
        }
        return candidates;
    }

    // 새 Interactions API의 raw REST 응답이 SDK 편의 필드(output_text)를 그대로 노출하는지
    // 문서상 보장되지 않으므로, 있으면 우선 쓰고 없으면 steps 배열의 model_output 단계를
    // 직접 훑어 텍스트를 찾는다 — 둘 중 하나에는 반드시 맞는다는 가정 없이 방어적으로 시도한다.
    private String extractOutputText(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode outputText = response.path("output_text");
        if (outputText.isString() && !outputText.asText("").isBlank()) {
            return outputText.asText();
        }
        JsonNode steps = response.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                if (!"model_output".equals(step.path("type").asText(""))) {
                    continue;
                }
                JsonNode content = step.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    if ("text".equals(part.path("type").asText(""))) {
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        return factory;
    }
}
