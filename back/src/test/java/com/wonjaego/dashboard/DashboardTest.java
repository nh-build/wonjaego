package com.wonjaego.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
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
    void 상품_수와_각_상품의_재고가_표시된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        createStockedVariant(session, "상품A", "10");
        createStockedVariant(session, "상품B", "20");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalProductCount", 2))
                .andExpect(content().string(containsString("상품A")))
                .andExpect(content().string(containsString("재고 10")))
                .andExpect(content().string(containsString("상품B")))
                .andExpect(content().string(containsString("재고 20")));
    }

    @Test
    void 재고가_기본값_5_이하인_변형만_품절임박_집계에_포함된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        createStockedVariant(session, "기본값초과", "6");
        createStockedVariant(session, "기본값이하", "5");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("lowStockVariantCount", 1L))
                .andExpect(content().string(containsString("기본값초과")))
                .andExpect(content().string(containsString("기본값이하")));
    }

    @Test
    void 다른_회원의_변형은_상품_목록_집계에_포함되지_않는다() throws Exception {
        MockHttpSession otherSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        createStockedVariant(otherSession, "남의상품", "1");

        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("남의상품"))))
                .andExpect(model().attribute("totalProductCount", 0));
    }

    @Test
    void 사진이_있는_상품은_대시보드_카드에_썸네일_이미지로_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        MockMultipartFile photo = new MockMultipartFile("photo", "photo.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/products")
                        .file(photo)
                        .session(session).with(csrf())
                        .param("name", "사진상품")
                        .param("price", "1000"))
                .andExpect(status().is3xxRedirection());

        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("사진상품"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/products/" + productId + "/photo")));
    }
}
