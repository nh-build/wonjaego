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

    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String TOOL_NAME = "suggest_product_names";
    private static final String TOOL_DESCRIPTION =
            "주어진 키워드를 참고해 온라인 쇼핑몰 상품명을 컨셉별로 하나씩 제안한다.";
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
    public List<NameSuggestion> suggest(List<String> keywords) {
        AnthropicMessageRequest request = new AnthropicMessageRequest(
                MODEL,
                512,
                List.of(new AnthropicMessageRequest.AnthropicTool(TOOL_NAME, TOOL_DESCRIPTION, INPUT_SCHEMA)),
                new AnthropicMessageRequest.AnthropicToolChoice("tool", TOOL_NAME),
                List.of(new AnthropicMessageRequest.AnthropicMessage("user", buildPrompt(keywords))));

        AnthropicMessageResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .body(request)
                    .retrieve()
                    .body(AnthropicMessageResponse.class);
        } catch (RestClientException e) {
            log.warn("Claude API 호출 실패. keywords={}", keywords, e);
            throw new NameSuggestionFailedException("Claude API 호출에 실패했습니다.", e);
        }

        return parse(response, keywords);
    }

    private String buildPrompt(List<String> keywords) {
        return "다음 키워드를 참고해서 온라인 쇼핑몰에 올릴 상품명 후보를 만들어줘. 키워드: "
                + String.join(", ", keywords)
                + ". 반드시 " + TOOL_NAME + " 도구를 사용해서 심플(simple), 러블리(lovely), 섹시(sexy), 캐주얼(casual) "
                + "4가지 분위기로 각각 상품명을 하나씩 지어줘. 각 이름은 15자 이내의 자연스러운 한국어 상품명이어야 해.";
    }

    private List<NameSuggestion> parse(AnthropicMessageResponse response, List<String> keywords) {
        if (response == null || response.content() == null) {
            log.warn("Claude API로부터 빈 응답을 받았습니다. keywords={}", keywords);
            throw new NameSuggestionFailedException("Claude API로부터 빈 응답을 받았습니다.");
        }

        Map<String, Object> input = response.content().stream()
                .filter(block -> "tool_use".equals(block.type()))
                .map(AnthropicMessageResponse.ContentBlock::input)
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Claude API 응답에서 도구 호출 결과를 찾을 수 없습니다. keywords={}", keywords);
                    return new NameSuggestionFailedException("Claude API 응답에서 도구 호출 결과를 찾을 수 없습니다.");
                });

        List<NameSuggestion> suggestions = new ArrayList<>();
        for (NamingConcept concept : NamingConcept.values()) {
            Object rawName = input.get(concept.name().toLowerCase());
            if (!(rawName instanceof String name) || name.isBlank()) {
                log.warn("Claude API 응답에 {} 상품명이 없습니다. keywords={}", concept.label(), keywords);
                throw new NameSuggestionFailedException("Claude API 응답에 " + concept.label() + " 상품명이 없습니다.");
            }
            suggestions.add(new NameSuggestion(concept, name.trim()));
        }
        return suggestions;
    }

    // Tied directly to NamingConcept so the tool schema and response-parsing keys can
    // never drift apart — adding a concept only requires touching the enum.
    private static Map<String, Object> buildInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (NamingConcept concept : NamingConcept.values()) {
            String key = concept.name().toLowerCase();
            properties.put(key, Map.of("type", "string"));
            required.add(key);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return factory;
    }
}
