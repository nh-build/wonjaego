package com.wonjaego.product;

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

// Covers GET /products/{id}/barcodes — the 옵션별 바코드 리스트 view/print screen linked
// from the detail page's "바코드 보기" row.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductBarcodesPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    private Product findByName(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void 바코드가_있는_변형은_바코드_값을_보여준다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodepageseller1", "password123", "가게1");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "바코드페이지상품").param("price", "10000")
                .param("optionGroups[0].name", "색상")
                .param("optionGroups[0].valuesText", "블랙, 화이트")
                .param("barcodesJson", "[\"WJG1001\",\"WJG1002\"]"));
        Product product = findByName("바코드페이지상품");

        mockMvc.perform(get("/products/" + product.getId() + "/barcodes").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WJG1001")))
                .andExpect(content().string(containsString("WJG1002")))
                .andExpect(content().string(containsString("블랙")))
                .andExpect(content().string(containsString("화이트")));
    }

    @Test
    void 바코드가_없는_변형은_미생성으로_표시된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "barcodepageseller2", "password123", "가게2");
        mockMvc.perform(post("/products").session(session).with(csrf())
                .param("name", "바코드없는페이지상품").param("price", "10000"));
        Product product = findByName("바코드없는페이지상품");

        mockMvc.perform(get("/products/" + product.getId() + "/barcodes").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("미생성")));
    }

    @Test
    void 다른_회원_소유_상품의_바코드_화면은_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "barcodepageseller3", "password123", "가게3");
        mockMvc.perform(post("/products").session(victimSession).with(csrf())
                .param("name", "피해자바코드상품").param("price", "10000"));
        Product product = findByName("피해자바코드상품");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "barcodepageseller4", "password123", "가게4");

        mockMvc.perform(get("/products/" + product.getId() + "/barcodes").session(attackerSession))
                .andExpect(status().isNotFound());
    }
}
