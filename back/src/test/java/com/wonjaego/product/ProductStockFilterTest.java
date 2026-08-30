package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.channel.SalesChannelRepository;
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

// Backs the dashboard's "재고 부족"/"품절" tiles: /products?stock=low|out narrows the list
// to products with at least one matching variant. isLowStock() (<=threshold) also covers
// stock 0, so a product with a genuinely-out-of-stock variant shows up under both filters.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductStockFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private SalesChannelRepository salesChannelRepository;

    // Creates a single-variant product and, unless stockQuantity is "0", stocks it via one
    // INBOUND movement — the default low-stock threshold (5) then decides low/out status.
    private void createStockedProduct(MockHttpSession session, String name, String stockQuantity) throws Exception {
        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", name)
                        .param("price", "1000"))
                .andExpect(status().is3xxRedirection());

        if (stockQuantity.equals("0")) {
            return;
        }
        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
        Long variantId = productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();
        Long channelId = createChannel(session, "채널_" + name);

        mockMvc.perform(post("/movements/new")
                        .session(session).with(csrf())
                        .param("variantId", String.valueOf(variantId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "INBOUND")
                        .param("quantity", stockQuantity)
                        .param("memo", ""))
                .andExpect(status().is3xxRedirection());
    }

    private Long createChannel(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/channels/tags").session(session).with(csrf()).param("name", name));
        return salesChannelRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void stock_low로_요청하면_재고부족_변형이_있는_상품만_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        createStockedProduct(session, "재고충분상품", "100");
        createStockedProduct(session, "재고부족상품", "3");

        mockMvc.perform(get("/products").param("stock", "low").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고부족상품")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("재고충분상품"))));
    }

    @Test
    void stock_out으로_요청하면_품절_변형이_있는_상품만_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        createStockedProduct(session, "재고있는상품", "3");
        createStockedProduct(session, "품절상품", "0");

        mockMvc.perform(get("/products").param("stock", "out").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("품절상품")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("재고있는상품"))));
    }

    @Test
    void stock_파라미터가_없으면_전체_상품이_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        createStockedProduct(session, "상품하나", "100");
        createStockedProduct(session, "상품둘", "0");

        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("상품하나")))
                .andExpect(content().string(containsString("상품둘")));
    }

    @Test
    void stock_파라미터가_low_out이_아니면_필터를_무시하고_전체_상품이_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        createStockedProduct(session, "무시테스트상품", "100");

        mockMvc.perform(get("/products").param("stock", "아무값").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("무시테스트상품")));
    }

    @Test
    void stock_out은_일부_옵션만_품절이면_보이지_않고_전체_품절이어야_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        mockMvc.perform(post("/products").session(session).with(csrf())
                        .param("name", "일부품절상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[0,20]"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/products").session(session).with(csrf())
                        .param("name", "전체품절상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[0,0]"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/products").param("stock", "out").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전체품절상품")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("일부품절상품"))));

        mockMvc.perform(get("/products").param("stock", "low").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("일부품절상품")))
                .andExpect(content().string(containsString("전체품절상품")));
    }

    @Test
    void 다른_회원의_상품은_필터_결과에_포함되지_않는다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        createStockedProduct(victimSession, "피해자품절상품", "0");

        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");

        mockMvc.perform(get("/products").param("stock", "out").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("피해자품절상품"))));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("피해자품절상품"))).isTrue();
    }
}
