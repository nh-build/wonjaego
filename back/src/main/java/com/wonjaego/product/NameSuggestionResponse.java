package com.wonjaego.product;

import com.wonjaego.ai.NameSuggestion;

public record NameSuggestionResponse(String concept, String label, String name) {

    static NameSuggestionResponse from(NameSuggestion suggestion) {
        return new NameSuggestionResponse(
                suggestion.concept().name(),
                suggestion.concept().label(),
                suggestion.name());
    }
}
