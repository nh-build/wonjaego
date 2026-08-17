package com.wonjaego.testsupport;

import com.wonjaego.ai.NameSuggestionClient;
import com.wonjaego.ai.NameSuggestionFailedException;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class StubNameSuggestionClientConfig {

    public static final String FAILURE_TRIGGER_KEYWORD = "__FAIL__";

    @Bean
    @Primary
    public NameSuggestionClient stubNameSuggestionClient() {
        return (keywords, mood) -> {
            if (keywords.contains(FAILURE_TRIGGER_KEYWORD)) {
                throw new NameSuggestionFailedException("stub-triggered failure for test");
            }
            // Echo mood into the response so tests can assert it actually reached the
            // client, rather than just asserting a fixed canned response regardless of input.
            String suffix = (mood == null || mood.isBlank()) ? "" : "(" + mood + ")";
            return List.of("이름1" + suffix, "이름2" + suffix, "이름3" + suffix, "이름4" + suffix, "이름5" + suffix);
        };
    }
}
