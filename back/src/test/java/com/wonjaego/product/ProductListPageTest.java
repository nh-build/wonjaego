package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.channel.ChannelCredentialService;
import com.wonjaego.channel.ChannelType;
import com.wonjaego.integration.zigzag.ZigzagProductImportService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// /products (list screen) — search, sort, pagination, and the option-summary/channel-source
// display that ProductService.listPage() computes. Stock filter (?stock=low|out) is already
// covered by ProductStockFilterTest, so it isn't repeated here.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(StubZigzagOpenApiClientConfig.class)
@Transactional
class ProductListPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ZigzagProductImportService zigzagProductImportService;

    @Autowired
    private com.wonjaego.integration.zigzag.ZigzagOpenApiClient zigzagOpenApiClient;

    @Autowired
    private ChannelCredentialService channelCredentialService;

    @Autowired
    private MemberRepository memberRepository;

    private void createProduct(MockHttpSession session, String name, String price) throws Exception {
        mockMvc.perform(post("/products").session(session).with(csrf()).param("name", name).param("price", price))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void 검색어로_상품명을_필터링할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "list1", "password123", "가게1");
        createProduct(session, "린넨 원피스", "10000");
        createProduct(session, "코튼 니트", "20000");

        mockMvc.perform(get("/products").param("q", "원피스").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("린넨 원피스")))
                .andExpect(content().string(not(containsString("코튼 니트"))));
    }

    @Test
    void sort가_stock이면_재고가_적은_상품부터_반환된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "list2", "password123", "가게2");
        mockMvc.perform(post("/products").session(session).with(csrf())
                        .param("name", "재고많음").param("price", "1000").param("stocksJson", "[20]"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/products").session(session).with(csrf())
                        .param("name", "재고적음").param("price", "1000").param("stocksJson", "[2]"))
                .andExpect(status().is3xxRedirection());

        MvcResult result = mockMvc.perform(get("/products/list-page").param("sort", "stock").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body.indexOf("재고적음")).isLessThan(body.indexOf("재고많음"));
    }

    @Test
    void 한_페이지에_20개씩_반환되고_다음_페이지_존재_여부가_hasNext에_반영된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "list3", "password123", "가게3");
        for (int i = 1; i <= 21; i++) {
            createProduct(session, "페이지상품" + i, "1000");
        }

        mockMvc.perform(get("/products/list-page").param("page", "0").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"totalCount\":21")))
                .andExpect(content().string(containsString("\"hasNext\":true")));

        mockMvc.perform(get("/products/list-page").param("page", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"hasNext\":false")));
    }

    @Test
    void 옵션이_두_개_이상이면_그룹별_요약과_전체_변형_수가_함께_표시된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "list4", "password123", "가게4");
        mockMvc.perform(post("/products").session(session).with(csrf())
                        .param("name", "옵션요약상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("optionGroups[1].name", "사이즈")
                        .param("optionGroups[1].valuesText", "S, M"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/products/list-page").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2색 · S,M · 4옵션")));
    }

    @Test
    void 옵션이_하나뿐이면_요약에_변형_수를_덧붙이지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "list5", "password123", "가게5");
        mockMvc.perform(post("/products").session(session).with(csrf())
                        .param("name", "단일옵션상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/products/list-page").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"optionSummary\":\"2색\"")));
    }

    @Test
    void 지그재그로_가져온_상품_개수와_채널_라벨이_표시된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "list6", "password123", "가게6");
        Long memberId = memberRepository.findAll().stream()
                .filter(m -> m.getUsername().equals("list6"))
                .findFirst()
                .orElseThrow()
                .getId();
        channelCredentialService.connect(memberId, ChannelType.ZIGZAG, "access", "secret");
        ((FakeZigzagOpenApiClient) zigzagOpenApiClient).respondWith("""
                {
                  "product_list": {
                    "item_list": [
                      {
                        "id": "P1", "name": "지그재그상품", "sales_status": "SALE", "display_status": "DISPLAY",
                        "image_list": [], "site_list": [], "option_list": [],
                        "item_list": [
                          { "id": "I1", "name": "단일", "item_code": "C1", "sales_status": "SALE",
                            "attribute_list": [], "inventory": {"quantity": 3},
                            "site_list": [ { "site": "ZIGZAG", "country": "KOR", "sales_price": 1000, "original_price": 12000 } ] }
                        ]
                      }
                    ]
                  }
                }
                """);
        zigzagProductImportService.importProducts(memberId);

        mockMvc.perform(get("/products/list-page").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"importedCount\":1")))
                .andExpect(content().string(containsString("\"channelLabel\":\"지그재그\"")));

        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("지그재그에서 1개 가져옴")));
    }
}
