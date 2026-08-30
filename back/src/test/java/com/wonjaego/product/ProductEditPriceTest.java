package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.testsupport.AuthTestSupport;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Covers the edit screen's price/costPrice fields (ProductEditForm.price/pricesJson/
// costPrice) — the edit form shares ProductCreateForm's shape (see ProductEditForm), so
// per-combo pricing goes through pricesJson (cartesianProduct order) rather than a
// variantId-keyed list. Option-composition editing itself is covered by
// ProductVariantCompositionEditTest.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductEditPriceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    private Product findByName(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void 원가를_입력하면_저장되고_기본가가_그대로면_변형_가격도_그대로다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "editpriceseller1", "password123", "가게1");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "원가수정상품").param("price", "10000"));
        Product product = findByName("원가수정상품");
        Long variantId = productVariantRepository.findAllByProductIdWithOptions(product.getId()).get(0).getId();

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "원가수정상품")
                        .param("price", "10000")
                        .param("costPrice", "6000"))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(product.getId()).orElseThrow().getCostPrice()).isEqualByComparingTo("6000");
        assertThat(productVariantRepository.findById(variantId).orElseThrow().getPrice()).isEqualByComparingTo("10000");
    }

    @Test
    void 옵션이_있는_상품의_조합별_가격을_pricesJson으로_각각_수정할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "editpriceseller2", "password123", "가게2");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "변형가격수정상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트"));
        Product product = findByName("변형가격수정상품");
        Map<String, ProductVariant> byLabel = productVariantRepository.findAllByProductIdWithOptions(product.getId()).stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
        Long groupId = byLabel.get("블랙").getOptionValues().iterator().next().getOptionGroup().getId();

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "변형가격수정상품")
                        .param("price", "10000")
                        .param("optionGroups[0].id", String.valueOf(groupId))
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("pricesJson", "[12000,13000]"))
                .andExpect(status().is3xxRedirection());

        assertThat(productVariantRepository.findById(byLabel.get("블랙").getId()).orElseThrow().getPrice()).isEqualByComparingTo("12000");
        assertThat(productVariantRepository.findById(byLabel.get("화이트").getId()).orElseThrow().getPrice()).isEqualByComparingTo("13000");
    }

    @Test
    void 다른_상품의_옵션_그룹_id로_수정하려_하면_거부되고_아무것도_바뀌지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "editpriceseller3", "password123", "가게3");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "상품A").param("price", "10000"));
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "상품B").param("price", "20000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙"));
        Product productA = findByName("상품A");
        Product productB = findByName("상품B");
        Long productBGroupId = productVariantRepository.findAllByProductIdWithOptions(productB.getId()).get(0)
                .getOptionValues().iterator().next().getOptionGroup().getId();

        mockMvc.perform(post("/products/" + productA.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "상품A")
                        .param("price", "10000")
                        .param("optionGroups[0].id", String.valueOf(productBGroupId))
                        .param("optionGroups[0].name", "아무거나")
                        .param("optionGroups[0].valuesText", "아무값"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("잘못된 옵션 정보입니다")));

        assertThat(productVariantRepository.findAllByProductIdWithOptions(productB.getId()).get(0).getOptionLabel())
                .isEqualTo("블랙");
        assertThat(productRepository.findById(productA.getId()).orElseThrow().getName()).isEqualTo("상품A");
    }
}
