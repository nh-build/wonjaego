package com.wonjaego.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class MemberAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    private MvcResult signUp(String username, String password, String businessName) throws Exception {
        return mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password)
                        .param("businessName", businessName))
                .andReturn();
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andReturn();
        HttpSession session = result.getRequest().getSession(false);
        return (MockHttpSession) session;
    }

    @Test
    void 회원가입하면_로그인_화면으로_리다이렉트되고_비밀번호는_해시로_저장된다() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "seller1")
                        .param("password", "password123")
                        .param("businessName", "누비상점"))
                .andExpect(redirectedUrl("/login?registered"));

        Member member = memberRepository.findByUsername("seller1").orElseThrow();
        assertThat(member.getBusinessName()).isEqualTo("누비상점");
        assertThat(member.getPassword()).isNotEqualTo("password123");
        assertThat(member.getPassword()).startsWith("$2");
    }

    @Test
    void 이미_존재하는_아이디로_가입하면_거부되고_계정이_중복_생성되지_않는다() throws Exception {
        signUp("seller2", "password123", "가게1");

        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "seller2")
                        .param("password", "password456")
                        .param("businessName", "가게2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이미 사용 중")));

        assertThat(memberRepository.findAll().stream()
                .filter(m -> m.getUsername().equals("seller2"))
                .count()).isEqualTo(1);
    }

    @Test
    void 로그인에_성공하면_세션이_생기고_실패하면_에러_화면으로_되돌아간다() throws Exception {
        signUp("seller3", "password123", "가게3");

        MockHttpSession session = login("seller3", "password123");
        assertThat(session).isNotNull();

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "seller3")
                        .param("password", "wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 로그인한_회원은_내_정보에서_본인의_username과_상호명을_확인할_수_있다() throws Exception {
        signUp("seller4", "password123", "네번째가게");
        MockHttpSession session = login("seller4", "password123");

        mockMvc.perform(get("/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("seller4")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("네번째가게")));
    }

    @Test
    void 로그인하지_않고_내_정보에_접근하면_로그인_화면으로_리다이렉트된다() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 로그아웃하면_세션이_종료되어_내_정보에_다시_접근할_수_없다() throws Exception {
        signUp("seller5", "password123", "다섯번째가게");
        MockHttpSession session = login("seller5", "password123");

        mockMvc.perform(get("/me").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/me").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
