package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class ProductVariantEditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    // Creates a product with no option groups, so it has exactly one (no-option) variant.
    private Long createVariant(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/products")
                .session(session)
                .with(csrf())
                .param("name", name)
                .param("price", "1000"));
        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
        return productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();
    }

    @Test
    void 변형의_SKU와_품절임박_기준을_수정할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        Long variantId = createVariant(session, "상품A");
        Long productId = productVariantRepository.findById(variantId).orElseThrow().getProduct().getId();

        mockMvc.perform(post("/products/" + productId + "/variants/" + variantId + "/edit")
                        .session(session).with(csrf())
                        .param("sku", "SKU-A")
                        .param("lowStockThreshold", "3"))
                .andExpect(status().is3xxRedirection());

        ProductVariant updated = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(updated.getSku()).isEqualTo("SKU-A");
        assertThat(updated.getLowStockThreshold()).isEqualTo(3);
    }

    @Test
    void SKU와_품절임박_기준을_비워둘_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        Long variantId = createVariant(session, "상품B");
        Long productId = productVariantRepository.findById(variantId).orElseThrow().getProduct().getId();

        mockMvc.perform(post("/products/" + productId + "/variants/" + variantId + "/edit")
                        .session(session).with(csrf())
                        .param("sku", "")
                        .param("lowStockThreshold", ""))
                .andExpect(status().is3xxRedirection());

        ProductVariant updated = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(updated.getSku()).isNull();
        assertThat(updated.getLowStockThreshold()).isNull();
        assertThat(updated.getEffectiveLowStockThreshold()).isEqualTo(5);

        mockMvc.perform(get("/products/" + productId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("기본값(5)")));
    }

    @Test
    void 같은_회원의_다른_변형과_SKU가_겹치면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        Long variantAId = createVariant(session, "상품C");
        Long variantBId = createVariant(session, "상품D");
        Long productAId = productVariantRepository.findById(variantAId).orElseThrow().getProduct().getId();
        Long productBId = productVariantRepository.findById(variantBId).orElseThrow().getProduct().getId();

        mockMvc.perform(post("/products/" + productAId + "/variants/" + variantAId + "/edit")
                .session(session).with(csrf())
                .param("sku", "SHARED-SKU")
                .param("lowStockThreshold", ""));

        mockMvc.perform(post("/products/" + productBId + "/variants/" + variantBId + "/edit")
                        .session(session).with(csrf())
                        .param("sku", "SHARED-SKU")
                        .param("lowStockThreshold", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 사용 중")));

        assertThat(productVariantRepository.findById(variantBId).orElseThrow().getSku()).isNull();
    }

    @Test
    void 자기_자신의_SKU는_그대로_저장해도_중복으로_거부되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        Long variantId = createVariant(session, "상품E");
        Long productId = productVariantRepository.findById(variantId).orElseThrow().getProduct().getId();

        mockMvc.perform(post("/products/" + productId + "/variants/" + variantId + "/edit")
                .session(session).with(csrf())
                .param("sku", "SKU-E")
                .param("lowStockThreshold", "2"));

        mockMvc.perform(post("/products/" + productId + "/variants/" + variantId + "/edit")
                        .session(session).with(csrf())
                        .param("sku", "SKU-E")
                        .param("lowStockThreshold", "4"))
                .andExpect(status().is3xxRedirection());

        ProductVariant updated = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(updated.getSku()).isEqualTo("SKU-E");
        assertThat(updated.getLowStockThreshold()).isEqualTo(4);
    }

    @Test
    void 다른_회원_소유_상품의_변형을_수정하려_하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        Long victimVariantId = createVariant(victimSession, "피해자상품");
        Long victimProductId = productVariantRepository.findById(victimVariantId).orElseThrow().getProduct().getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");

        mockMvc.perform(post("/products/" + victimProductId + "/variants/" + victimVariantId + "/edit")
                        .session(attackerSession).with(csrf())
                        .param("sku", "탈취")
                        .param("lowStockThreshold", ""))
                .andExpect(status().isNotFound());

        assertThat(productVariantRepository.findById(victimVariantId).orElseThrow().getSku()).isNull();
    }

    @Test
    void 다른_회원_소유_변형은_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        Long victimVariantId = createVariant(victimSession, "피해자상품2");
        Long victimProductId = productVariantRepository.findById(victimVariantId).orElseThrow().getProduct().getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");

        // lowStockThreshold=-1 fails @Min(0) — ownership must still be enforced first.
        mockMvc.perform(post("/products/" + victimProductId + "/variants/" + victimVariantId + "/edit")
                        .session(attackerSession).with(csrf())
                        .param("sku", "")
                        .param("lowStockThreshold", "-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_상품에_속한_변형_id로_접근하면_404() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");
        Long variantAId = createVariant(session, "상품F");
        Long variantBId = createVariant(session, "상품G");
        Long productAId = productVariantRepository.findById(variantAId).orElseThrow().getProduct().getId();

        // variantBId belongs to a different product than productAId, even though both
        // products are owned by the same member.
        mockMvc.perform(post("/products/" + productAId + "/variants/" + variantBId + "/edit")
                        .session(session).with(csrf())
                        .param("sku", "")
                        .param("lowStockThreshold", ""))
                .andExpect(status().isNotFound());
    }

    @Test
    void 옵션_값이_두_개_이상인_변형도_수정_화면에서_정상적으로_저장된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "옵션상품")
                        .param("price", "1000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("optionGroups[1].name", "사이즈")
                        .param("optionGroups[1].valuesText", "S, M"))
                .andExpect(status().is3xxRedirection());

        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("옵션상품"))
                .findFirst()
                .orElseThrow()
                .getId();
        // Each variant here carries 2 option values (e.g. 블랙+S) — exercises the edit
        // screen's fetch of a variant whose findByIdAndMemberId query joins a collection
        // (optionValues) while returning a single Optional<ProductVariant>.
        Long variantId = productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();

        mockMvc.perform(get("/products/" + productId + "/variants/" + variantId + "/edit").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/products/" + productId + "/variants/" + variantId + "/edit")
                        .session(session).with(csrf())
                        .param("sku", "OPT-SKU-1")
                        .param("lowStockThreshold", "2"))
                .andExpect(status().is3xxRedirection());

        ProductVariant updated = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(updated.getSku()).isEqualTo("OPT-SKU-1");
        assertThat(updated.getLowStockThreshold()).isEqualTo(2);
    }
}
