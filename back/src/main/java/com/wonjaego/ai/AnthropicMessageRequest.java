package com.wonjaego.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record AnthropicMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        List<AnthropicTool> tools,
        @JsonProperty("tool_choice") AnthropicToolChoice toolChoice,
        List<AnthropicMessage> messages) {

    record AnthropicTool(String name, String description, @JsonProperty("input_schema") Object inputSchema) {
    }

    record AnthropicToolChoice(String type, String name) {
    }

    record AnthropicMessage(String role, String content) {
    }
}
