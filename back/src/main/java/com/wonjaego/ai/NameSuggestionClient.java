package com.wonjaego.ai;

import java.util.List;

public interface NameSuggestionClient {

    /**
     * Always returns exactly 5 product name suggestions combining the given point words,
     * shaped by the optional mood/concept text (may be null or blank to omit it).
     */
    List<String> suggest(List<String> keywords, String mood);
}
