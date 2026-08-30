package com.wonjaego.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class StockEntryLookupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    private Long createProduct(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/products")
                .session(session).with(csrf())
                .param("name", name)
                .param("price", "1000"));
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private void setSku(MockHttpSession session, Long productId, Long variantId, String sku) throws Exception {
        mockMvc.perform(post("/products/" + productId + "/variants/" + variantId + "/edit")
                .session(session).with(csrf())
                .param("sku", sku));
    }

    private Long createProductWithPhoto(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(multipart("/products")
                        .file(new MockMultipartFile("photo", "product.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes()))
                        .session(session).with(csrf())
                        .param("name", name)
                        .param("price", "1000"))
                .andExpect(status().is3xxRedirection());
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void 상품명_검색은_부분일치_대소문자무관으로_소유_상품만_반환한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        createProduct(session, "베이직 반팔티");
        createProduct(session, "슬림 청바지");

        mockMvc.perform(get("/products/search").param("q", "반팔").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("베이직 반팔티"));
    }

    @Test
    void 검색어가_비어있으면_빈_목록을_반환한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        createProduct(session, "상품A");

        mockMvc.perform(get("/products/search").param("q", "  ").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 검색은_다른_회원의_상품을_반환하지_않는다() throws Exception {
        MockHttpSession otherSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        createProduct(otherSession, "남의상품");

        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");

        mockMvc.perform(get("/products/search").param("q", "남의").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 검색_결과는_20개로_제한된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller13", "password123", "가게13");
        for (int i = 0; i < 25; i++) {
            createProduct(session, "검색상품" + i);
        }

        mockMvc.perform(get("/products/search").param("q", "검색상품").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void 상품_ID로_조합별_재고_정보를_조회할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        Long productId = createProduct(session, "상품B");
        Long variantId = productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();

        mockMvc.perform(get("/products/" + productId + "/stock-entry").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.productName").value("상품B"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.matchedSku").doesNotExist())
                .andExpect(jsonPath("$.variants[0].id").value(variantId))
                .andExpect(jsonPath("$.variants[0].stockQuantity").value(0));
    }

    @Test
    void 검색_결과에_상품_이미지_URL이_포함된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller14", "password123", "가게14");
        Long productId = createProductWithPhoto(session, "사진있는검색상품");

        mockMvc.perform(get("/products/search").param("q", "사진있는검색상품").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl").value("/products/" + productId + "/photo"));
    }

    @Test
    void 사진이_있는_상품은_stock_entry_응답에_이미지_URL이_포함된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller15", "password123", "가게15");
        Long productId = createProductWithPhoto(session, "사진있는상품");

        mockMvc.perform(get("/products/" + productId + "/stock-entry").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/products/" + productId + "/photo"));
    }

    @Test
    void 다른_회원의_상품ID로_조회하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        Long victimProductId = createProduct(victimSession, "피해자상품");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");

        mockMvc.perform(get("/products/" + victimProductId + "/stock-entry").session(attackerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void 바코드로_상품과_조합_정보를_조회할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");
        Long productId = createProduct(session, "상품C");
        Long variantId = productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();
        setSku(session, productId, variantId, "8801234567890");

        mockMvc.perform(get("/products/by-sku").param("sku", "8801234567890").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.matchedSku").value("8801234567890"));
    }

    @Test
    void 존재하지_않는_바코드는_404() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");

        mockMvc.perform(get("/products/by-sku").param("sku", "없는바코드").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void 빈_바코드는_404() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");

        mockMvc.perform(get("/products/by-sku").param("sku", "   ").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_회원의_바코드로는_조회할_수_없다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller11", "password123", "가게11");
        Long victimProductId = createProduct(victimSession, "피해자상품2");
        Long victimVariantId = productVariantRepository.findAllByProductIdWithOptions(victimProductId).get(0).getId();
        setSku(victimSession, victimProductId, victimVariantId, "9990001112223");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller12", "password123", "가게12");

        mockMvc.perform(get("/products/by-sku").param("sku", "9990001112223").session(attackerSession))
                .andExpect(status().isNotFound());
    }
}
