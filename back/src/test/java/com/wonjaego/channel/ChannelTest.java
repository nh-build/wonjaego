package com.wonjaego.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.member.MemberRepository;
import com.wonjaego.testsupport.AuthTestSupport;
import com.wonjaego.testsupport.FakeZigzagOpenApiClient;
import com.wonjaego.testsupport.StubZigzagOpenApiClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// "연동 먼저 → 관리화면에서 역할 선택" — /channels (screen 1, connect-only) and /channels/{id}
// (screen 2, 연동 상태 + 역할 선택 + 상품 불러오기). Distinct from /channels/tags (SalesChannel).
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(StubZigzagOpenApiClientConfig.class)
@Transactional
class ChannelTest {

    private static final String ONE_PRODUCT_ONE_ITEM = """
            {
              "product_list": {
                "item_list": [
                  {
                    "id": "P1", "name": "베이직 티셔츠", "sales_status": "SALE", "display_status": "DISPLAY",
                    "image_list": [], "site_list": [], "option_list": [],
                    "item_list": [
                      { "id": "I1", "name": "단일", "item_code": "C1", "sales_status": "SALE",
                        "attribute_list": [], "inventory": {"quantity": 3},
                        "site_list": [ { "site": "ZIGZAG", "country": "KOR", "sales_price": 1000, "original_price": 19000 } ] }
                    ]
                  }
                ]
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChannelCredentialRepository channelCredentialRepository;

    @Autowired
    private ChannelCredentialService channelCredentialService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private com.wonjaego.integration.zigzag.ZigzagOpenApiClient zigzagOpenApiClient;

    private FakeZigzagOpenApiClient fakeClient() {
        return (FakeZigzagOpenApiClient) zigzagOpenApiClient;
    }

    private Long memberId(String username) {
        return memberRepository.findAll().stream()
                .filter(m -> m.getUsername().equals(username))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private String connectAndExtractManageUrl(MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/channels/connect").session(session).with(csrf())
                        .param("channelType", "ZIGZAG")
                        .param("accessKey", "access")
                        .param("secretKey", "secret"))
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    void 화면1은_채널_카드와_에이블리_준비중을_보여준다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch1", "password123", "가게1");

        mockMvc.perform(get("/channels").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("지그재그")))
                .andExpect(content().string(containsString("연동하기")))
                .andExpect(content().string(containsString("에이블리")))
                .andExpect(content().string(containsString("준비중")));
    }

    @Test
    void 연동하면_관리화면으로_이동하고_기본_역할은_주문연동이다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch2", "password123", "가게2");

        String manageUrl = connectAndExtractManageUrl(session);

        mockMvc.perform(get(manageUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("연동됨")))
                .andExpect(content().string(containsString("주문 연동")))
                .andExpect(content().string(containsString("상품 소스")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("지그재그 상품 불러오기"))));
    }

    @Test
    void 역할을_상품_소스로_바꾸면_상품_불러오기_버튼이_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch3", "password123", "가게3");
        String manageUrl = connectAndExtractManageUrl(session);

        mockMvc.perform(post(manageUrl + "/role").session(session).with(csrf()).param("role", "PRODUCT_SOURCE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(manageUrl));

        mockMvc.perform(get(manageUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("지그재그 상품 불러오기")));
    }

    @Test
    void 상품_소스는_한_회원당_하나만_지정할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch4", "password123", "가게4");
        Long memberId = memberId("ch4");
        String zigzagManageUrl = connectAndExtractManageUrl(session);
        mockMvc.perform(post(zigzagManageUrl + "/role").session(session).with(csrf()).param("role", "PRODUCT_SOURCE"));

        // COUPANG isn't reachable via the connect screen yet (ZIGZAG-only) — seed it directly
        // through the service, the same way any future channel's connect flow would.
        ChannelCredential coupang = channelCredentialService.connect(memberId, ChannelType.COUPANG, "a", "b");

        mockMvc.perform(post("/channels/" + coupang.getId() + "/role").session(session).with(csrf())
                        .param("role", "PRODUCT_SOURCE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/channels/" + coupang.getId()));

        assertThat(channelCredentialRepository.findById(coupang.getId()).orElseThrow().getRole())
                .isEqualTo(ChannelRole.ORDER_SYNC);
    }

    @Test
    void 해제하면_키는_지워지고_행은_유지된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch5", "password123", "가게5");
        String manageUrl = connectAndExtractManageUrl(session);
        mockMvc.perform(post(manageUrl + "/role").session(session).with(csrf()).param("role", "PRODUCT_SOURCE"));

        mockMvc.perform(post(manageUrl + "/disconnect").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/channels"));

        ChannelCredential credential = channelCredentialRepository
                .findByMemberIdAndChannelType(memberId("ch5"), ChannelType.ZIGZAG).orElseThrow();
        assertThat(credential.getStatus()).isEqualTo(ChannelConnectionStatus.NOT_CONNECTED);
        assertThat(credential.getEncryptedAccessKey()).isNull();
        assertThat(credential.getEncryptedSecretKey()).isNull();
        assertThat(credential.getRole()).isEqualTo(ChannelRole.PRODUCT_SOURCE);
    }

    @Test
    void 재연동하면_이전_역할이_유지된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch6", "password123", "가게6");
        String manageUrl = connectAndExtractManageUrl(session);
        mockMvc.perform(post(manageUrl + "/role").session(session).with(csrf()).param("role", "PRODUCT_SOURCE"));
        mockMvc.perform(post(manageUrl + "/disconnect").session(session).with(csrf()));

        mockMvc.perform(post("/channels/connect").session(session).with(csrf())
                .param("channelType", "ZIGZAG").param("accessKey", "new-access").param("secretKey", "new-secret"));

        ChannelCredential credential = channelCredentialRepository
                .findByMemberIdAndChannelType(memberId("ch6"), ChannelType.ZIGZAG).orElseThrow();
        assertThat(credential.getStatus()).isEqualTo(ChannelConnectionStatus.CONNECTED);
        assertThat(credential.getRole()).isEqualTo(ChannelRole.PRODUCT_SOURCE);
    }

    @Test
    void 상품_불러오기_버튼을_누르면_지그재그_상품을_가져오고_기록이_남는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch7", "password123", "가게7");
        String manageUrl = connectAndExtractManageUrl(session);
        mockMvc.perform(post(manageUrl + "/role").session(session).with(csrf()).param("role", "PRODUCT_SOURCE"));
        fakeClient().respondWith(ONE_PRODUCT_ONE_ITEM);

        mockMvc.perform(post(manageUrl + "/import").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(manageUrl));

        mockMvc.perform(get(manageUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1개 상품을 가져왔어요")));

        ChannelCredential credential = channelCredentialRepository
                .findByMemberIdAndChannelType(memberId("ch7"), ChannelType.ZIGZAG).orElseThrow();
        assertThat(credential.getLastImportedCount()).isEqualTo(1);
        assertThat(credential.getLastImportedAt()).isNotNull();
    }

    @Test
    void 주문연동_역할인_채널에서_상품_불러오기를_요청하면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "ch8", "password123", "가게8");
        String manageUrl = connectAndExtractManageUrl(session);
        // role은 기본값 ORDER_SYNC 그대로 둔다.

        mockMvc.perform(post(manageUrl + "/import").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(manageUrl));

        mockMvc.perform(get(manageUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("상품 소스로 지정된 지그재그 채널만")));
    }

    @Test
    void 다른_회원_소유_채널의_관리화면에_접근하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "ch9", "password123", "가게9");
        String manageUrl = connectAndExtractManageUrl(victimSession);

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "ch10", "password123", "가게10");

        mockMvc.perform(get(manageUrl).session(attackerSession))
                .andExpect(status().isNotFound());
    }
}
