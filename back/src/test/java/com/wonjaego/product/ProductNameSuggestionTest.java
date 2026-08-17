package com.wonjaego.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.testsupport.AuthTestSupport;
import com.wonjaego.testsupport.StubNameSuggestionClientConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(StubNameSuggestionClientConfig.class)
@Transactional
class ProductNameSuggestionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 정상_포인트_단어로_요청하면_이름_5개를_받는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "namesuggest1", "password123", "가게1");

        mockMvc.perform(post("/products/name-suggestions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":\"린넨, 여름, 원피스\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(5)))
                .andExpect(jsonPath("$[0]").value("이름1"));
    }

    @Test
    void 컨셉_무드_없이도_요청할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "namesuggest5", "password123", "가게5");

        mockMvc.perform(post("/products/name-suggestions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":\"린넨, 여름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(5)));
    }

    @Test
    void 컨셉_무드를_함께_보내면_그대로_전달된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "namesuggest6", "password123", "가게6");

        mockMvc.perform(post("/products/name-suggestions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":\"린넨, 여름\", \"mood\":\"미니멀하게\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(5)))
                .andExpect(jsonPath("$[0]").value("이름1(미니멀하게)"));
    }

    @Test
    void 포인트_단어를_입력하지_않으면_요청이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "namesuggest2", "password123", "가게2");

        mockMvc.perform(post("/products/name-suggestions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":\" , , \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않은_상태로_요청하면_거부된다() throws Exception {
        mockMvc.perform(post("/products/name-suggestions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":\"니트\"}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void CSRF_토큰_없이_요청하면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "namesuggest3", "password123", "가게3");

        mockMvc.perform(post("/products/name-suggestions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":\"니트\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void AI_호출이_실패하면_502로_응답한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "namesuggest4", "password123", "가게4");

        mockMvc.perform(post("/products/name-suggestions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":\"" + StubNameSuggestionClientConfig.FAILURE_TRIGGER_KEYWORD + "\"}"))
                .andExpect(status().isBadGateway());
    }
}
