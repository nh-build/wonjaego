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

    private void createProduct(MockHttpSession session, String name, String sku, String price, String stockQuantity)
            throws Exception {
        mockMvc.perform(post("/products")
                .session(session)
                .with(csrf())
                .param("name", name)
                .param("sku", sku)
                .param("price", price)
                .param("stockQuantity", stockQuantity));
    }

    private Product findBySku(String sku) {
        return productRepository.findAll().stream()
                .filter(p -> p.getSku().equals(sku))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 로그인한_회원은_상품을_등록하고_목록에서_확인할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");

        mockMvc.perform(post("/products")
                        .session(session)
                        .with(csrf())
                        .param("name", "티셔츠")
                        .param("sku", "TSHIRT-001")
                        .param("price", "19900")
                        .param("stockQuantity", "10")
                        .param("lowStockThreshold", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"));

        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("티셔츠")));
    }

    @Test
    void 같은_회원_안에서_SKU가_중복되면_등록이_거부되고_중복_생성되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        createProduct(session, "상품A", "DUP-001", "1000", "5");

        mockMvc.perform(post("/products")
                        .session(session)
                        .with(csrf())
                        .param("name", "상품B")
                        .param("sku", "DUP-001")
                        .param("price", "2000")
                        .param("stockQuantity", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 사용 중")));

        assertThat(productRepository.findAll().stream().filter(p -> p.getSku().equals("DUP-001")).count())
                .isEqualTo(1);
    }

    @Test
    void 초기_총재고에_음수를_입력하면_등록이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");

        mockMvc.perform(post("/products")
                        .session(session)
                        .with(csrf())
                        .param("name", "상품")
                        .param("sku", "NEG-001")
                        .param("price", "1000")
                        .param("stockQuantity", "-1"))
                .andExpect(status().isOk());

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getSku().equals("NEG-001"))).isFalse();
    }

    @Test
    void 다른_회원의_목록에는_내_상품이_섞이지_않는다() throws Exception {
        MockHttpSession session1 = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        createProduct(session1, "회원4상품", "M4-001", "1000", "1");

        MockHttpSession session2 = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");

        mockMvc.perform(get("/products").session(session2))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("회원4상품"))));
    }

    @Test
    void 다른_회원_소유_상품_id로_상세에_접근하면_404() throws Exception {
        MockHttpSession session1 = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        createProduct(session1, "회원6상품", "M6-001", "1000", "1");
        Long productId = findBySku("M6-001").getId();

        MockHttpSession session2 = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");

        mockMvc.perform(get("/products/" + productId).session(session2))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상품_정보를_수정할_수_있고_재고는_이_화면에서_바뀌지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");
        createProduct(session, "원래이름", "EDIT-001", "1000", "7");
        Long productId = findBySku("EDIT-001").getId();

        mockMvc.perform(post("/products/" + productId + "/edit")
                        .session(session)
                        .with(csrf())
                        .param("name", "바뀐이름")
                        .param("sku", "EDIT-001")
                        .param("price", "2000")
                        .param("lowStockThreshold", "2"))
                .andExpect(status().is3xxRedirection());

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("바뀐이름");
        assertThat(updated.getPrice()).isEqualByComparingTo("2000");
        assertThat(updated.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void 수정시_다른_상품과_SKU가_겹치면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");
        createProduct(session, "상품A", "KEEP-001", "1000", "1");
        createProduct(session, "상품B", "EDIT-002", "1000", "1");
        Long productBId = findBySku("EDIT-002").getId();

        mockMvc.perform(post("/products/" + productBId + "/edit")
                        .session(session)
                        .with(csrf())
                        .param("name", "상품B")
                        .param("sku", "KEEP-001")
                        .param("price", "1000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 사용 중")));

        assertThat(productRepository.findById(productBId).orElseThrow().getSku()).isEqualTo("EDIT-002");
    }

    @Test
    void 다른_회원_소유_상품은_SKU가_겹쳐도_수정할_수_없고_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");
        createProduct(victimSession, "피해자상품", "VICTIM-001", "1000", "1");
        Long victimProductId = findBySku("VICTIM-001").getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller11", "password123", "가게11");
        createProduct(attackerSession, "공격자상품", "ATTACKER-001", "1000", "1");

        // Attacker already owns a product with SKU "ATTACKER-001" — this must still 404
        // on ownership, not fall through to a duplicate-SKU error.
        mockMvc.perform(post("/products/" + victimProductId + "/edit")
                        .session(attackerSession)
                        .with(csrf())
                        .param("name", "탈취시도")
                        .param("sku", "ATTACKER-001")
                        .param("price", "1"))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(victimProductId).orElseThrow().getSku()).isEqualTo("VICTIM-001");
    }

    @Test
    void 다른_회원_소유_상품은_수정_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller16", "password123", "가게16");
        createProduct(victimSession, "피해자상품2", "VICTIM-003", "1000", "1");
        Long victimProductId = findBySku("VICTIM-003").getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller17", "password123", "가게17");

        // A blank name fails @NotBlank validation before update()/getOwned() would
        // normally run — ownership must still be enforced first.
        mockMvc.perform(post("/products/" + victimProductId + "/edit")
                        .session(attackerSession)
                        .with(csrf())
                        .param("name", "")
                        .param("sku", "WHATEVER")
                        .param("price", "1"))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(victimProductId).orElseThrow().getSku()).isEqualTo("VICTIM-003");
    }

    @Test
    void 상품을_삭제할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller12", "password123", "가게12");
        createProduct(session, "삭제될상품", "DEL-001", "1000", "1");
        Long productId = findBySku("DEL-001").getId();

        mockMvc.perform(post("/products/" + productId + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(productId)).isEmpty();
    }

    @Test
    void 다른_회원_소유_상품은_삭제할_수_없고_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller13", "password123", "가게13");
        createProduct(victimSession, "피해자상품2", "VICTIM-002", "1000", "1");
        Long victimProductId = findBySku("VICTIM-002").getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller14", "password123", "가게14");

        mockMvc.perform(post("/products/" + victimProductId + "/delete").session(attackerSession).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(victimProductId)).isPresent();
    }
}
