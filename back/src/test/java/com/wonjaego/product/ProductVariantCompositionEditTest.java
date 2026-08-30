package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

// The product-edit screen reuses the registration form (products/product-form.html), so
// option composition is editable there too — unlike the old "light edit" screen, which kept
// option composition immutable. These tests pin down ProductService.update()'s core
// contract: existing ProductVariant rows (stock, barcode, movement history) survive an
// option-composition edit whenever possible, and are only ever dropped when provably safe
// (no movement history at all — see VariantHasMovementsException).
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductVariantCompositionEditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private OptionValueRepository optionValueRepository;

    private Product findByName(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private Map<String, ProductVariant> variantsByLabel(Long productId) {
        return productVariantRepository.findAllByProductIdWithOptions(productId).stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
    }

    @Test
    void 옵션값을_추가하면_기존_조합의_재고와_바코드는_유지되고_새_조합만_재고0으로_생긴다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit1", "password123", "가게1");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "값추가상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트")
                .param("stocksJson", "[10,5]")
                .param("autoGenerateBarcode", "true"));
        Product product = findByName("값추가상품");
        Map<String, ProductVariant> before = variantsByLabel(product.getId());
        Long blackId = before.get("블랙").getId();
        Long whiteId = before.get("화이트").getId();
        String blackBarcode = before.get("블랙").getBarcode();
        Long groupId = before.get("블랙").getOptionValues().iterator().next().getOptionGroup().getId();

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "값추가상품")
                        .param("price", "10000")
                        .param("optionGroups[0].id", String.valueOf(groupId))
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트, 베이지")
                        .param("pricesJson", "[10000,10000,10000]")
                        .param("autoGenerateBarcode", "true"))
                .andExpect(status().is3xxRedirection());

        Map<String, ProductVariant> after = variantsByLabel(product.getId());
        assertThat(after).hasSize(3);
        assertThat(after.get("블랙").getId()).isEqualTo(blackId);
        assertThat(after.get("블랙").getStockQuantity()).isEqualTo(10);
        assertThat(after.get("블랙").getBarcode()).isEqualTo(blackBarcode);
        assertThat(after.get("화이트").getId()).isEqualTo(whiteId);
        assertThat(after.get("화이트").getStockQuantity()).isEqualTo(5);
        assertThat(after.get("베이지").getStockQuantity()).isEqualTo(0);
        assertThat(after.get("베이지").getBarcode()).isNotBlank();
        assertThat(after.get("베이지").getBarcode()).isNotEqualTo(blackBarcode);
    }

    @Test
    void 새_옵션_그룹을_추가하면_기존_조합은_첫_새_조합으로_확장되고_나머지는_재고0으로_생긴다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit2", "password123", "가게2");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "그룹추가상품").param("price", "10000")
                .param("stocksJson", "[7]"));
        Product product = findByName("그룹추가상품");
        Long originalVariantId = productVariantRepository.findAllByProductIdWithOptions(product.getId()).get(0).getId();

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "그룹추가상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "사이즈")
                        .param("optionGroups[0].valuesText", "S, M")
                        .param("pricesJson", "[10000,10000]"))
                .andExpect(status().is3xxRedirection());

        Map<String, ProductVariant> after = variantsByLabel(product.getId());
        assertThat(after).hasSize(2);
        assertThat(after.get("S").getId()).isEqualTo(originalVariantId);
        assertThat(after.get("S").getStockQuantity()).isEqualTo(7);
        assertThat(after.get("M").getStockQuantity()).isEqualTo(0);
        assertThat(after.get("M").getId()).isNotEqualTo(originalVariantId);
    }

    @Test
    void 재고가_없는_옵션값을_삭제하면_해당_조합이_안전하게_삭제된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit3", "password123", "가게3");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "값삭제상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트")
                .param("stocksJson", "[0,10]"));
        Product product = findByName("값삭제상품");
        Map<String, ProductVariant> before = variantsByLabel(product.getId());
        Long blackId = before.get("블랙").getId();
        Long whiteId = before.get("화이트").getId();
        Long groupId = before.get("블랙").getOptionValues().iterator().next().getOptionGroup().getId();

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "값삭제상품")
                        .param("price", "10000")
                        .param("optionGroups[0].id", String.valueOf(groupId))
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "화이트")
                        .param("pricesJson", "[10000]"))
                .andExpect(status().is3xxRedirection());

        assertThat(productVariantRepository.findById(blackId)).isEmpty();
        assertThat(productVariantRepository.findById(whiteId)).isPresent();
        assertThat(productVariantRepository.findById(whiteId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(optionValueRepository.findByOptionGroupIdAndValue(groupId, "블랙")).isEmpty();
    }

    @Test
    void 재고가_있는_옵션값을_삭제하려_하면_거부되고_아무것도_바뀌지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit4", "password123", "가게4");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "재고삭제거부상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트")
                .param("stocksJson", "[5,10]"));
        Product product = findByName("재고삭제거부상품");
        Map<String, ProductVariant> before = variantsByLabel(product.getId());
        Long groupId = before.get("블랙").getOptionValues().iterator().next().getOptionGroup().getId();

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "재고삭제거부상품")
                        .param("price", "10000")
                        .param("optionGroups[0].id", String.valueOf(groupId))
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "화이트")
                        .param("pricesJson", "[10000]"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고 기록이 있는 옵션은 삭제할 수 없어요")))
                .andExpect(content().string(containsString("블랙")));

        Map<String, ProductVariant> after = variantsByLabel(product.getId());
        assertThat(after).hasSize(2);
        assertThat(after.get("블랙").getStockQuantity()).isEqualTo(5);
        assertThat(after.get("화이트").getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 옵션_그룹_이름을_바꿔도_기존_조합_데이터는_유지된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit5", "password123", "가게5");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "그룹이름변경상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙")
                .param("stocksJson", "[3]"));
        Product product = findByName("그룹이름변경상품");
        ProductVariant before = productVariantRepository.findAllByProductIdWithOptions(product.getId()).get(0);
        Long variantId = before.getId();
        Long groupId = before.getOptionValues().iterator().next().getOptionGroup().getId();

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "그룹이름변경상품")
                        .param("price", "10000")
                        .param("optionGroups[0].id", String.valueOf(groupId))
                        .param("optionGroups[0].name", "컬러")
                        .param("optionGroups[0].valuesText", "블랙")
                        .param("pricesJson", "[10000]"))
                .andExpect(status().is3xxRedirection());

        ProductVariant after = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(after.getStockQuantity()).isEqualTo(3);
        assertThat(after.getOptionValues().iterator().next().getOptionGroup().getName()).isEqualTo("컬러");
    }

    @Test
    void 수정_화면_기본가는_최저_변형_가격으로_프리필된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit6", "password123", "가게6");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "프리필상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트")
                .param("pricesJson", "[15000,9000]"));
        Product product = findByName("프리필상품");

        // Server renders the value pre-formatted with thousands commas and no decimals (KRW
        // has no minor unit) via #numbers.formatInteger — not the raw BigDecimal.toString()
        // (which would carry the column's scale=2, e.g. "9000.00"). The closing quote in the
        // match matters: it rules out a lingering ".00" after "9,000".
        mockMvc.perform(get("/products/" + product.getId() + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"9,000\"")));
    }

    @Test
    void 모든_기존_바코드가_있으면_자동생성_체크박스가_기본_체크로_프리필된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit7", "password123", "가게7");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "바코드체크상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트")
                .param("autoGenerateBarcode", "true"));
        Product product = findByName("바코드체크상품");

        mockMvc.perform(get("/products/" + product.getId() + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("checked=\"checked\"")));
    }

    @Test
    void 바코드가_없는_변형이_있으면_자동생성_체크박스가_기본_해제로_프리필된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "compedit8", "password123", "가게8");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "바코드미체크상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트"));
        Product product = findByName("바코드미체크상품");

        mockMvc.perform(get("/products/" + product.getId() + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("checked=\"checked\""))));
    }
}
