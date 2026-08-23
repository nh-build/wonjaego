package com.wonjaego.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.channel.SalesChannelRepository;
import com.wonjaego.product.ProductRepository;
import com.wonjaego.product.ProductVariantRepository;
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
class DashboardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private SalesChannelRepository salesChannelRepository;

    // Creates a product with no option groups (one variant), then stocks it via a single
    // INBOUND movement. There's no threshold-editing screen yet in this ticket, so every
    // test here relies on the system default threshold (5) to determine the low-stock
    // boundary rather than setting a per-variant threshold directly.
    private Long createStockedVariant(MockHttpSession session, String name, String stockQuantity) throws Exception {
        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", name)
                        .param("price", "1000"))
                .andExpect(status().is3xxRedirection());

        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
        Long variantId = productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();

        if (!stockQuantity.equals("0")) {
            Long channelId = createChannel(session, "입고용_" + name);
            mockMvc.perform(post("/movements/new")
                            .session(session).with(csrf())
                            .param("variantId", String.valueOf(variantId))
                            .param("salesChannelId", String.valueOf(channelId))
                            .param("type", "INBOUND")
                            .param("quantity", stockQuantity)
                            .param("memo", ""))
                    .andExpect(status().is3xxRedirection());
        }
        return variantId;
    }

    private Long createChannel(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/channels").session(session).with(csrf()).param("name", name));
        return salesChannelRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void 상품_수가_요약_타일에_표시된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        createStockedVariant(session, "상품A", "10");
        createStockedVariant(session, "상품B", "20");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalProductCount", 2));
    }

    @Test
    void 재고가_기본값_5_이하인_변형만_품절임박_집계에_포함된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        createStockedVariant(session, "기본값초과", "6");
        createStockedVariant(session, "기본값이하", "5");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("lowStockVariantCount", 1L));
    }

    @Test
    void 다른_회원의_변형은_상품_목록_집계에_포함되지_않는다() throws Exception {
        MockHttpSession otherSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        createStockedVariant(otherSession, "남의상품", "1");

        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalProductCount", 0));
    }

    @Test
    void 재고_부족_품절_타일은_필터된_상품_목록으로_연결되고_빠른_작업_카드는_해당_화면으로_연결된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/products?stock=low\"")))
                .andExpect(content().string(containsString("href=\"/products?stock=out\"")))
                .andExpect(content().string(containsString("href=\"/movements/new?type=INBOUND\"")))
                .andExpect(content().string(containsString("href=\"/movements/new?type=SALE\"")))
                .andExpect(content().string(containsString("href=\"/products#product-list\"")))
                .andExpect(content().string(containsString("입고하기")))
                .andExpect(content().string(containsString("출고하기")))
                .andExpect(content().string(containsString("상품 등록")))
                .andExpect(content().string(containsString("상품 목록")));
    }
}
