package com.wonjaego.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class SalesChannelCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SalesChannelRepository salesChannelRepository;

    private void createChannel(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/channels")
                .session(session)
                .with(csrf())
                .param("name", name));
    }

    private SalesChannel findByName(String name) {
        return salesChannelRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 로그인한_회원은_채널을_등록하고_목록에서_확인할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");

        mockMvc.perform(post("/channels")
                        .session(session)
                        .with(csrf())
                        .param("name", "스마트스토어"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/channels"));

        mockMvc.perform(get("/channels").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("스마트스토어")));
    }

    @Test
    void 같은_회원_안에서_이름이_중복되면_등록이_거부되고_중복_생성되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        createChannel(session, "에이블리");

        mockMvc.perform(post("/channels")
                        .session(session)
                        .with(csrf())
                        .param("name", "에이블리"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 사용 중")));

        assertThat(salesChannelRepository.findAll().stream().filter(c -> c.getName().equals("에이블리")).count())
                .isEqualTo(1);
    }

    @Test
    void 다른_회원의_목록에는_내_채널이_섞이지_않는다() throws Exception {
        MockHttpSession session1 = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        createChannel(session1, "회원3채널");

        MockHttpSession session2 = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");

        mockMvc.perform(get("/channels").session(session2))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("회원3채널"))));
    }

    @Test
    void 채널_이름을_수정할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        createChannel(session, "원래이름");
        Long channelId = findByName("원래이름").getId();

        mockMvc.perform(post("/channels/" + channelId + "/edit")
                        .session(session)
                        .with(csrf())
                        .param("name", "바뀐이름"))
                .andExpect(status().is3xxRedirection());

        assertThat(salesChannelRepository.findById(channelId).orElseThrow().getName()).isEqualTo("바뀐이름");
    }

    @Test
    void 수정시_다른_채널과_이름이_겹치면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        createChannel(session, "지그재그");
        createChannel(session, "바꿀채널");
        Long channelId = findByName("바꿀채널").getId();

        mockMvc.perform(post("/channels/" + channelId + "/edit")
                        .session(session)
                        .with(csrf())
                        .param("name", "지그재그"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 사용 중")));

        assertThat(salesChannelRepository.findById(channelId).orElseThrow().getName()).isEqualTo("바꿀채널");
    }

    @Test
    void 다른_회원_소유_채널의_수정_화면에_접근하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        createChannel(victimSession, "피해자채널");
        Long victimChannelId = findByName("피해자채널").getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");

        mockMvc.perform(get("/channels/" + victimChannelId + "/edit").session(attackerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_회원_소유_채널은_이름이_겹쳐도_수정할_수_없고_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");
        createChannel(victimSession, "피해자채널2");
        Long victimChannelId = findByName("피해자채널2").getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");
        createChannel(attackerSession, "공격자채널");

        // Attacker already owns a channel named "공격자채널" — this must still 404 on
        // ownership, not fall through to a duplicate-name error.
        mockMvc.perform(post("/channels/" + victimChannelId + "/edit")
                        .session(attackerSession)
                        .with(csrf())
                        .param("name", "공격자채널"))
                .andExpect(status().isNotFound());

        assertThat(salesChannelRepository.findById(victimChannelId).orElseThrow().getName()).isEqualTo("피해자채널2");
    }

    @Test
    void 다른_회원_소유_채널은_수정_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller14", "password123", "가게14");
        createChannel(victimSession, "피해자채널4");
        Long victimChannelId = findByName("피해자채널4").getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller15", "password123", "가게15");

        // A blank name fails @NotBlank validation before update()/getOwned() would
        // normally run — ownership must still be enforced first.
        mockMvc.perform(post("/channels/" + victimChannelId + "/edit")
                        .session(attackerSession)
                        .with(csrf())
                        .param("name", ""))
                .andExpect(status().isNotFound());

        assertThat(salesChannelRepository.findById(victimChannelId).orElseThrow().getName()).isEqualTo("피해자채널4");
    }

    @Test
    void 채널을_삭제할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller11", "password123", "가게11");
        createChannel(session, "삭제될채널");
        Long channelId = findByName("삭제될채널").getId();

        mockMvc.perform(post("/channels/" + channelId + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(salesChannelRepository.findById(channelId)).isEmpty();
    }

    @Test
    void 다른_회원_소유_채널은_삭제할_수_없고_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller12", "password123", "가게12");
        createChannel(victimSession, "피해자채널3");
        Long victimChannelId = findByName("피해자채널3").getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller13", "password123", "가게13");

        mockMvc.perform(post("/channels/" + victimChannelId + "/delete").session(attackerSession).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(salesChannelRepository.findById(victimChannelId)).isPresent();
    }
}
