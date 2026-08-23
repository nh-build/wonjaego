package com.wonjaego.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.product.ProductRepository;
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

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(StubZigzagOpenApiClientConfig.class)
@Transactional
class ChannelConnectTest {

    private static final String ONE_PRODUCT_ONE_ITEM = """
            {
              "product_list": {
                "item_list": [
                  {
                    "id": "P1",
                    "name": "베이직 티셔츠",
                    "price": { "original_price": 19000 },
                    "item_list": [
                      { "id": "I1", "attribute_list": [], "inventory": {"quantity": 7} }
                    ]
                  }
                ]
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.wonjaego.integration.zigzag.ZigzagOpenApiClient zigzagOpenApiClient;

    @Autowired
    private ChannelCredentialRepository channelCredentialRepository;

    @Autowired
    private ProductRepository productRepository;

    private FakeZigzagOpenApiClient fakeClient() {
        return (FakeZigzagOpenApiClient) zigzagOpenApiClient;
    }

    @Test
    void 채널_연동_화면은_지그재그만_활성화되고_나머지는_준비중이다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "conn1", "password123", "가게1");

        mockMvc.perform(get("/channels/connect").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("채널 연동")))
                .andExpect(content().string(containsString("지그재그")))
                .andExpect(content().string(containsString("연동 가능")))
                .andExpect(content().string(containsString("준비중")));
    }

    @Test
    void API_키_발급_방법_도움말이_4단계와_경고_문구_파트너센터_링크를_포함한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "conn6", "password123", "가게6");

        mockMvc.perform(get("/channels/connect").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("API 키 발급 방법 보기")))
                .andExpect(content().string(containsString("지그재그 파트너센터에 로그인")))
                .andExpect(content().string(containsString("[스토어 정보 관리] → [API 인증키 관리] 메뉴로 이동")))
                .andExpect(content().string(containsString("[인증키 발급] 버튼을 누른다")))
                .andExpect(content().string(containsString("→ Access Key·Secret Key가 팝업으로 떠요")))
                .andExpect(content().string(containsString("복사한 두 키를 앱 연동 화면에 붙여넣기")))
                .andExpect(content().string(containsString("Secret Key는 발급 팝업에서 딱 한 번만 보여요")))
                .andExpect(content().string(not(containsString("GET-PRODUCT(상품조회)를 꼭 체크"))))
                .andExpect(content().string(containsString("href=\"https://partners.kakaostyle.com\"")));
    }

    @Test
    void 키를_입력하고_연동하면_암호화되어_저장되고_상품이_가져와진다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "conn2", "password123", "가게2");
        fakeClient().respondWith(ONE_PRODUCT_ONE_ITEM);

        mockMvc.perform(post("/channels/connect").session(session).with(csrf())
                        .param("channelType", "ZIGZAG")
                        .param("accessKey", "my-access")
                        .param("secretKey", "my-secret"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1개 상품을 가져왔어요")));

        var products = productRepository.findAll().stream()
                .filter(p -> "베이직 티셔츠".equals(p.getName()))
                .toList();
        assertThat(products).hasSize(1);

        var credential = channelCredentialRepository.findByMemberIdAndChannelType(
                products.get(0).getMember().getId(), ChannelType.ZIGZAG);
        assertThat(credential).isPresent();
        assertThat(credential.get().getEncryptedAccessKey()).isNotEqualTo("my-access");
        assertThat(credential.get().getEncryptedSecretKey()).isNotEqualTo("my-secret");
    }

    @Test
    void 키를_비워두고_제출하면_에러가_표시되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "conn3", "password123", "가게3");

        mockMvc.perform(post("/channels/connect").session(session).with(csrf())
                        .param("channelType", "ZIGZAG")
                        .param("accessKey", "")
                        .param("secretKey", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Access Key와 Secret Key를 모두 입력해주세요")));
    }

    @Test
    void 지그재그_API_에러가_나면_에러_메시지가_그대로_표시된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "conn4", "password123", "가게4");
        fakeClient().failWith(FakeZigzagOpenApiClient.authError());

        mockMvc.perform(post("/channels/connect").session(session).with(csrf())
                        .param("channelType", "ZIGZAG")
                        .param("accessKey", "bad-access")
                        .param("secretKey", "bad-secret"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("invalid access key")));
    }

    @Test
    void 내_정보와_상품_등록_화면에서_채널_연동_화면으로_이동할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "conn5", "password123", "가게5");

        mockMvc.perform(get("/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/channels/connect\"")));

        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/channels/connect\"")));
    }
}
