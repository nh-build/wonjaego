package com.wonjaego.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.testsupport.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 프로필 수정(/me/edit)·비밀번호 변경(/me/password) — 더보기/설정 재구조화(이전 세션)에 이어
// 내 정보 화면 자체를 손댄 첫 케이스라, 세션 캐시된 principal이 아니라 항상 DB를 다시
// 읽는지(staleness 회귀)를 특히 신경 써서 검증한다.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class MemberProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 회원가입_시_이메일을_선택으로_입력할_수_있다() throws Exception {
        mockMvc.perform(post("/signup")
                .with(csrf())
                .param("username", "profileuser1")
                .param("password", "password123")
                .param("businessName", "가게1")
                .param("email", "seller1@example.com"));

        Member member = memberRepository.findByUsername("profileuser1").orElseThrow();
        assertThat(member.getEmail()).isEqualTo("seller1@example.com");
    }

    @Test
    void 이메일_없이도_회원가입된다() throws Exception {
        mockMvc.perform(post("/signup")
                .with(csrf())
                .param("username", "profileuser2")
                .param("password", "password123")
                .param("businessName", "가게2"));

        Member member = memberRepository.findByUsername("profileuser2").orElseThrow();
        assertThat(member.getEmail()).isNullOrEmpty();
    }

    @Test
    void 프로필_수정_화면은_기존_이름을_프리필하고_이메일은_비활성으로_보여준다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "profileuser3", "password123", "원래가게이름");

        mockMvc.perform(get("/me/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"원래가게이름\"")))
                .andExpect(content().string(containsString("disabled")));
    }

    @Test
    void 이름을_수정하면_내_정보_화면에_즉시_반영된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "profileuser4", "password123", "예전이름");

        mockMvc.perform(post("/me/edit").session(session).with(csrf())
                        .param("businessName", "새이름"))
                .andExpect(redirectedUrl("/me"));

        // principal.getMember()가 아니라 DB를 다시 읽어야 세션 재로그인 없이도 바로 보인다.
        mockMvc.perform(get("/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("새이름")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("예전이름"))));

        assertThat(memberRepository.findByUsername("profileuser4").orElseThrow().getBusinessName())
                .isEqualTo("새이름");
    }

    @Test
    void 빈_이름으로는_프로필을_수정할_수_없다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "profileuser5", "password123", "유지될이름");

        mockMvc.perform(post("/me/edit").session(session).with(csrf())
                        .param("businessName", ""))
                .andExpect(status().isOk());

        assertThat(memberRepository.findByUsername("profileuser5").orElseThrow().getBusinessName())
                .isEqualTo("유지될이름");
    }

    @Test
    void 올바른_현재_비밀번호로_새_비밀번호를_설정하면_새_비밀번호로_로그인된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "profileuser6", "password123", "가게6");

        mockMvc.perform(post("/me/password").session(session).with(csrf())
                        .param("currentPassword", "password123")
                        .param("newPassword", "newpassword456")
                        .param("newPasswordConfirm", "newpassword456"))
                .andExpect(redirectedUrl("/me"));

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "profileuser6")
                        .param("password", "newpassword456"))
                .andExpect(redirectedUrl("/"));

        Member member = memberRepository.findByUsername("profileuser6").orElseThrow();
        assertThat(member.getPassword()).startsWith("$2");
    }

    @Test
    void 현재_비밀번호가_틀리면_비밀번호가_바뀌지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "profileuser7", "password123", "가게7");

        mockMvc.perform(post("/me/password").session(session).with(csrf())
                        .param("currentPassword", "wrong-password")
                        .param("newPassword", "newpassword456")
                        .param("newPasswordConfirm", "newpassword456"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("현재 비밀번호가 올바르지 않습니다")));

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "profileuser7")
                        .param("password", "password123"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 새_비밀번호_확인이_일치하지_않으면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "profileuser8", "password123", "가게8");

        mockMvc.perform(post("/me/password").session(session).with(csrf())
                        .param("currentPassword", "password123")
                        .param("newPassword", "newpassword456")
                        .param("newPasswordConfirm", "다른값"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("일치하지 않습니다")));

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "profileuser8")
                        .param("password", "password123"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 내_정보_화면엔_더_이상_판매채널_연동_링크가_없다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "profileuser9", "password123", "가게9");

        mockMvc.perform(get("/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/channels\""))));
    }
}
