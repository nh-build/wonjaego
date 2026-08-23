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

// Covers the registration-time "기본가 + 옵션값 추가금" pricing flow (docs/adr/0008): price
// lives on ProductVariant, not Product — the registration screen auto-calculates each combo's
// price client-side and submits the final numbers as pricesJson, parallel to stocksJson.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductRegistrationPriceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    private Product findByName(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 조합별_가격을_입력하면_각_변형에_그대로_저장된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "priceseller1", "password123", "가게1");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "가격조합상품")
                        .param("price", "19000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[5,3]")
                        .param("pricesJson", "[19000,21000]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("가격조합상품");
        Map<String, ProductVariant> byLabel = productVariantRepository.findAllByProductIdWithOptions(product.getId()).stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
        assertThat(byLabel.get("블랙").getPrice()).isEqualByComparingTo("19000");
        assertThat(byLabel.get("화이트").getPrice()).isEqualByComparingTo("21000");
    }

    @Test
    void pricesJson을_비워두면_모든_조합이_기본가로_채워진다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "priceseller2", "password123", "가게2");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "기본가상품")
                        .param("price", "15000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[1,1]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("기본가상품");
        assertThat(productVariantRepository.findAllByProductIdWithOptions(product.getId()))
                .allSatisfy(variant -> assertThat(variant.getPrice()).isEqualByComparingTo("15000"));
    }

    @Test
    void 옵션이_없으면_기본가가_그_상품_유일한_변형의_가격이_된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "priceseller3", "password123", "가게3");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "단일변형상품")
                        .param("price", "9900"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("단일변형상품");
        ProductVariant variant = productVariantRepository.findAllByProductIdWithOptions(product.getId()).get(0);
        assertThat(variant.getPrice()).isEqualByComparingTo("9900");
    }

    @Test
    void 가격_입력_개수가_조합_개수와_다르면_등록이_거부되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "priceseller4", "password123", "가게4");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "가격개수불일치상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[1,1]")
                        .param("pricesJson", "[10000]"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("가격 입력 개수가 옵션 조합 개수와 일치하지 않습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("가격개수불일치상품"))).isFalse();
    }

    @Test
    void 가격_데이터가_올바른_JSON이_아니면_등록이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "priceseller5", "password123", "가게5");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "잘못된가격JSON상품")
                        .param("price", "10000")
                        .param("pricesJson", "not-json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("가격 데이터 형식이 올바르지 않습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("잘못된가격JSON상품"))).isFalse();
    }

    @Test
    void 가격에_음수가_있으면_등록이_거부되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "priceseller6", "password123", "가게6");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "음수가격상품")
                        .param("price", "10000")
                        .param("pricesJson", "[-500]"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("가격은 0 이상의 숫자여야 합니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("음수가격상품"))).isFalse();
    }

    // Blank pricesJson defaults every combo to the base price (parsePrices() can't invent
    // surcharges the client never sent) — so a negative base price must be rejected at the
    // form boundary, not just when pricesJson happens to be present.
    @Test
    void 기본가가_음수이고_pricesJson이_비어있으면_등록이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "priceseller7", "password123", "가게7");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "음수기본가상품")
                        .param("price", "-1000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("기본가는 0 이상이어야 합니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("음수기본가상품"))).isFalse();
    }
}
