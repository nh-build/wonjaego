package com.wonjaego.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record AnthropicMessageResponse(List<ContentBlock> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, Map<String, Object> input) {
    }
}
