package com.wonjaego.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class AnthropicNameSuggestionClient implements NameSuggestionClient {

    private static final int SUGGESTION_COUNT = 5;
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String TOOL_NAME = "suggest_product_names";
    private static final String TOOL_DESCRIPTION =
            "주어진 포인트 단어를 조합해 온라인 쇼핑몰 상품명 후보를 " + SUGGESTION_COUNT + "개 제안한다.";
    private static final Map<String, Object> INPUT_SCHEMA = buildInputSchema();

    private final RestClient restClient;

    public AnthropicNameSuggestionClient(@Value("${wonjaego.ai.anthropic.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Override
    public List<String> suggest(List<String> keywords, String mood) {
        AnthropicMessageRequest request = new AnthropicMessageRequest(
                MODEL,
                512,
                List.of(new AnthropicMessageRequest.AnthropicTool(TOOL_NAME, TOOL_DESCRIPTION, INPUT_SCHEMA)),
                new AnthropicMessageRequest.AnthropicToolChoice("tool", TOOL_NAME),
                List.of(new AnthropicMessageRequest.AnthropicMessage("user", buildPrompt(keywords, mood))));

        AnthropicMessageResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .body(request)
                    .retrieve()
                    .body(AnthropicMessageResponse.class);
        } catch (RestClientException e) {
            log.warn("Claude API 호출 실패. keywords={}, mood={}", keywords, mood, e);
            throw new NameSuggestionFailedException("Claude API 호출에 실패했습니다.", e);
        }

        return parse(response, keywords, mood);
    }

    private String buildPrompt(List<String> keywords, String mood) {
        StringBuilder prompt = new StringBuilder("다음 포인트 단어를 참고해서 온라인 쇼핑몰에 올릴 상품명 후보를 만들어줘. 포인트 단어: ")
                .append(String.join(", ", keywords))
                .append(".");
        if (mood != null && !mood.isBlank()) {
            prompt.append(" 원하는 컨셉/무드: ").append(mood.trim()).append(".");
        }
        prompt.append(" 반드시 ").append(TOOL_NAME).append(" 도구를 사용해서 서로 다른 상품명 후보를 ")
                .append(SUGGESTION_COUNT).append("개 지어줘. 각 이름은 15자 이내의 자연스러운 한국어 상품명이어야 해.");
        return prompt.toString();
    }

    private List<String> parse(AnthropicMessageResponse response, List<String> keywords, String mood) {
        if (response == null || response.content() == null) {
            log.warn("Claude API로부터 빈 응답을 받았습니다. keywords={}, mood={}", keywords, mood);
            throw new NameSuggestionFailedException("Claude API로부터 빈 응답을 받았습니다.");
        }

        Map<String, Object> input = response.content().stream()
                .filter(block -> "tool_use".equals(block.type()))
                .map(AnthropicMessageResponse.ContentBlock::input)
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Claude API 응답에서 도구 호출 결과를 찾을 수 없습니다. keywords={}, mood={}", keywords, mood);
                    return new NameSuggestionFailedException("Claude API 응답에서 도구 호출 결과를 찾을 수 없습니다.");
                });

        Object rawNames = input.get("names");
        if (!(rawNames instanceof List<?> rawList) || rawList.size() != SUGGESTION_COUNT) {
            log.warn("Claude API 응답의 이름 개수가 올바르지 않습니다. keywords={}, mood={}, response={}", keywords, mood, rawNames);
            throw new NameSuggestionFailedException("Claude API 응답의 상품명 개수가 올바르지 않습니다.");
        }

        List<String> suggestions = new ArrayList<>();
        for (Object rawName : rawList) {
            if (!(rawName instanceof String name) || name.isBlank()) {
                log.warn("Claude API 응답에 빈 상품명이 포함되어 있습니다. keywords={}, mood={}", keywords, mood);
                throw new NameSuggestionFailedException("Claude API 응답에 빈 상품명이 포함되어 있습니다.");
            }
            suggestions.add(name.trim());
        }
        return suggestions;
    }

    private static Map<String, Object> buildInputSchema() {
        Map<String, Object> namesProperty = new LinkedHashMap<>();
        namesProperty.put("type", "array");
        namesProperty.put("items", Map.of("type", "string"));
        namesProperty.put("minItems", SUGGESTION_COUNT);
        namesProperty.put("maxItems", SUGGESTION_COUNT);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("names", namesProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("names"));
        return schema;
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return factory;
    }
}
