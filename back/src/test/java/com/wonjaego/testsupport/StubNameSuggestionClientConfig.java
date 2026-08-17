package com.wonjaego.testsupport;

import com.wonjaego.ai.NameSuggestion;
import com.wonjaego.ai.NameSuggestionClient;
import com.wonjaego.ai.NameSuggestionFailedException;
import com.wonjaego.ai.NamingConcept;
import java.util.Arrays;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class StubNameSuggestionClientConfig {

    public static final String FAILURE_TRIGGER_KEYWORD = "__FAIL__";

    @Bean
    @Primary
    public NameSuggestionClient stubNameSuggestionClient() {
        return keywords -> {
            if (keywords.contains(FAILURE_TRIGGER_KEYWORD)) {
                throw new NameSuggestionFailedException("stub-triggered failure for test");
            }
            return Arrays.stream(NamingConcept.values())
                    .map(concept -> new NameSuggestion(concept, concept.label() + " 상품명"))
                    .toList();
        };
    }
}
