package com.wonjaego.ai;

import java.util.List;

public interface NameSuggestionClient {

    /**
     * Always returns exactly one {@link NameSuggestion} per {@link NamingConcept}, in
     * {@link NamingConcept#values()} order.
     */
    List<NameSuggestion> suggest(List<String> keywords);
}
