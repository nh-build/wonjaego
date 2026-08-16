package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.testsupport.AuthTestSupport;
import java.util.List;
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

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    private void createProduct(MockHttpSession session, String name, String price) throws Exception {
        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", name)
                        .param("price", price))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"));
    }

    private void createProductWithOptions(MockHttpSession session, String name, String price,
                                           String group1Name, String group1Values,
                                           String group2Name, String group2Values) throws Exception {
        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", name)
                        .param("price", price)
                        .param("optionGroups[0].name", group1Name)
                        .param("optionGroups[0].valuesText", group1Values)
                        .param("optionGroups[1].name", group2Name)
                        .param("optionGroups[1].valuesText", group2Values))
                .andExpect(status().is3xxRedirection());
    }

    private Product findByName(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 옵션_없이_상품을_등록하면_조합_없는_변형_1개가_생성된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        createProduct(session, "무옵션상품", "10000");

        Product product = findByName("무옵션상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());

        assertThat(variants).hasSize(1);
        assertThat(variants.get(0).getOptionLabel()).isEmpty();
        assertThat(variants.get(0).getStockQuantity()).isEqualTo(0);
        assertThat(variants.get(0).getSku()).isNull();
    }

    @Test
    void 옵션_그룹_두_개를_입력하면_모든_조합이_자동_생성된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        createProductWithOptions(session, "옵션상품", "10000", "색상", "블랙, 화이트", "사이즈", "S, M");

        Product product = findByName("옵션상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());

        assertThat(variants).hasSize(4);
        assertThat(variants.stream().map(ProductVariant::getOptionLabel).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("블랙 / S", "블랙 / M", "화이트 / S", "화이트 / M");
    }

    @Test
    void 옵션_값의_앞뒤_공백과_중복은_정리된_뒤_조합이_생성된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        createProductWithOptions(session, "중복옵션상품", "10000", "색상", "블랙 , 블랙, 화이트 ", "사이즈", "S");

        Product product = findByName("중복옵션상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());

        assertThat(variants).hasSize(2);
        assertThat(variants.stream().map(ProductVariant::getOptionLabel))
                .containsExactlyInAnyOrder("블랙 / S", "화이트 / S");
    }

    @Test
    void 콤마만_입력된_옵션_그룹은_빈_슬롯처럼_무시되고_조합_없는_변형_1개가_생성된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller16", "password123", "가게16");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "콤마만있는상품")
                        .param("price", "1000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", ",,,"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("콤마만있는상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());

        assertThat(variants).hasSize(1);
        assertThat(variants.get(0).getOptionLabel()).isEmpty();
    }

    @Test
    void 상품_목록에서_로그인한_회원_소유_상품만_조회된다() throws Exception {
        MockHttpSession session1 = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        createProduct(session1, "회원4상품", "1000");

        MockHttpSession session2 = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");

        mockMvc.perform(get("/products").session(session2))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("회원4상품"))));
    }

    @Test
    void 상품_상세에서_변형_목록을_옵션_라벨과_함께_볼_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        createProductWithOptions(session, "상세조회상품", "1000", "색상", "블랙", "사이즈", "S, M");
        Product product = findByName("상세조회상품");

        mockMvc.perform(get("/products/" + product.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("블랙 / S")))
                .andExpect(content().string(containsString("블랙 / M")));
    }

    @Test
    void 다른_회원_소유_상품_id로_상세에_접근하면_404() throws Exception {
        MockHttpSession session1 = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        createProduct(session1, "회원7상품", "1000");
        Product product = findByName("회원7상품");

        MockHttpSession session2 = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");

        mockMvc.perform(get("/products/" + product.getId()).session(session2))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상품명과_가격을_수정할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");
        createProduct(session, "원래이름", "1000");
        Product product = findByName("원래이름");

        mockMvc.perform(post("/products/" + product.getId() + "/edit")
                        .session(session).with(csrf())
                        .param("name", "바뀐이름")
                        .param("price", "2000"))
                .andExpect(status().is3xxRedirection());

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("바뀐이름");
        assertThat(updated.getPrice()).isEqualByComparingTo("2000");
    }

    @Test
    void 다른_회원_소유_상품은_수정_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");
        createProduct(victimSession, "피해자상품", "1000");
        Product victimProduct = findByName("피해자상품");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller11", "password123", "가게11");

        // A blank name fails @NotBlank validation before update() would normally run —
        // ownership must still be enforced first.
        mockMvc.perform(post("/products/" + victimProduct.getId() + "/edit")
                        .session(attackerSession).with(csrf())
                        .param("name", "")
                        .param("price", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상품을_삭제할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller12", "password123", "가게12");
        createProduct(session, "삭제될상품", "1000");
        Product product = findByName("삭제될상품");

        mockMvc.perform(post("/products/" + product.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(product.getId())).isEmpty();
    }

    @Test
    void 다른_회원_소유_상품은_삭제할_수_없고_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller13", "password123", "가게13");
        createProduct(victimSession, "피해자상품2", "1000");
        Product victimProduct = findByName("피해자상품2");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller14", "password123", "가게14");

        mockMvc.perform(post("/products/" + victimProduct.getId() + "/delete").session(attackerSession).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(victimProduct.getId())).isPresent();
    }
}
