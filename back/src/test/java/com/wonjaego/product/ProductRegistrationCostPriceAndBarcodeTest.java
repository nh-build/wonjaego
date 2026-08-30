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

// Covers the registration screen's optional 원가 입력 (Product.costPrice) and 바코드 생성
// (ProductVariant.barcode) additions — both are optional, parallel to the existing
// stocksJson/pricesJson mechanism (barcodesJson, same combo-order contract).
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductRegistrationCostPriceAndBarcodeTest {

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
    void 원가를_입력하면_상품에_저장된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "costseller1", "password123", "가게1");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "원가상품")
                        .param("price", "19000")
                        .param("costPrice", "10000"))
                .andExpect(status().is3xxRedirection());

        assertThat(findByName("원가상품").getCostPrice()).isEqualByComparingTo("10000");
    }

    @Test
    void 원가를_비워두면_null로_저장된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "costseller2", "password123", "가게2");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "원가없는상품")
                        .param("price", "19000"))
                .andExpect(status().is3xxRedirection());

        assertThat(findByName("원가없는상품").getCostPrice()).isNull();
    }

    @Test
    void 원가에_음수가_있으면_등록이_거부되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "costseller3", "password123", "가게3");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "음수원가상품")
                        .param("price", "19000")
                        .param("costPrice", "-500"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("원가는 0 이상이어야 합니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("음수원가상품"))).isFalse();
    }

    @Test
    void 조합별_바코드를_입력하면_각_변형에_그대로_저장된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodeseller1", "password123", "가게1");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "바코드조합상품")
                        .param("price", "19000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[5,3]")
                        .param("barcodesJson", "[\"WJG0001\",\"WJG0002\"]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("바코드조합상품");
        Map<String, ProductVariant> byLabel = productVariantRepository.findAllByProductIdWithOptions(product.getId()).stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
        assertThat(byLabel.get("블랙").getBarcode()).isEqualTo("WJG0001");
        assertThat(byLabel.get("화이트").getBarcode()).isEqualTo("WJG0002");
    }

    @Test
    void barcodesJson을_비워두면_바코드가_생성되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodeseller2", "password123", "가게2");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "바코드없는상품")
                        .param("price", "15000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[1,1]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("바코드없는상품");
        assertThat(productVariantRepository.findAllByProductIdWithOptions(product.getId()))
                .allSatisfy(variant -> assertThat(variant.getBarcode()).isNull());
    }

    @Test
    void 바코드_입력_개수가_조합_개수와_다르면_등록이_거부되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodeseller3", "password123", "가게3");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "바코드개수불일치상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[1,1]")
                        .param("barcodesJson", "[\"WJG0001\"]"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("바코드 입력 개수가 옵션 조합 개수와 일치하지 않습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("바코드개수불일치상품"))).isFalse();
    }

    @Test
    void 바코드가_중복되면_등록이_거부되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodeseller4", "password123", "가게4");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "중복바코드상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[1,1]")
                        .param("barcodesJson", "[\"WJG0001\",\"WJG0001\"]"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("바코드는 중복될 수 없습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("중복바코드상품"))).isFalse();
    }

    @Test
    void autoGenerateBarcode를_체크하면_변형마다_고유_바코드가_자동_발급된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodeseller6", "password123", "가게6");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "자동바코드상품")
                        .param("price", "12000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[1,1]")
                        .param("autoGenerateBarcode", "true"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("자동바코드상품");
        var variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());
        assertThat(variants).allSatisfy(variant -> assertThat(variant.getBarcode()).isNotBlank());
        assertThat(variants.stream().map(ProductVariant::getBarcode).distinct().count()).isEqualTo(variants.size());
    }

    @Test
    void autoGenerateBarcode를_체크해도_명시적으로_입력한_바코드는_그대로_쓰인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodeseller7", "password123", "가게7");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "혼합바코드상품")
                        .param("price", "12000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[1,1]")
                        .param("barcodesJson", "[\"WJGCUSTOM\",\"\"]")
                        .param("autoGenerateBarcode", "true"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("혼합바코드상품");
        Map<String, ProductVariant> byLabel = productVariantRepository.findAllByProductIdWithOptions(product.getId()).stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
        assertThat(byLabel.get("블랙").getBarcode()).isEqualTo("WJGCUSTOM");
        assertThat(byLabel.get("화이트").getBarcode()).isNotBlank().isNotEqualTo("WJGCUSTOM");
    }

    @Test
    void 바코드_데이터가_올바른_JSON이_아니면_등록이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodeseller5", "password123", "가게5");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "잘못된바코드JSON상품")
                        .param("price", "10000")
                        .param("barcodesJson", "not-json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("바코드 데이터 형식이 올바르지 않습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("잘못된바코드JSON상품"))).isFalse();
    }
}
