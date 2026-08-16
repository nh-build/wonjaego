package com.wonjaego.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class DashboardTest {

    @Autowired
    private MockMvc mockMvc;

    private void createProduct(MockHttpSession session, String name, String sku, String stockQuantity, String lowStockThreshold)
            throws Exception {
        var request = post("/products")
                .session(session)
                .with(csrf())
                .param("name", name)
                .param("sku", sku)
                .param("price", "1000")
                .param("stockQuantity", stockQuantity);
        if (lowStockThreshold != null) {
            request.param("lowStockThreshold", lowStockThreshold);
        }
        mockMvc.perform(request).andExpect(status().is3xxRedirection());
    }

    @Test
    void 상품_수와_총재고_합계가_표시된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        createProduct(session, "상품A", "SKU-A", "10", null);
        createProduct(session, "상품B", "SKU-B", "20", null);

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("상품 수: 2")))
                .andExpect(content().string(containsString("총재고 합계: 30")));
    }

    @Test
    void 품절임박_기준이_설정된_상품은_그_값_기준으로_판정된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        createProduct(session, "기준초과", "SKU-C", "10", "3");
        createProduct(session, "기준이하", "SKU-D", "3", "3");

        String body = mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("기준초과");
        assertThat(body).contains("기준이하");
    }

    @Test
    void 품절임박_기준이_없으면_기본값_5_이하일_때_품절임박_목록에_포함된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        createProduct(session, "기본값초과", "SKU-E", "6", null);
        createProduct(session, "기본값이하", "SKU-F", "5", null);

        String body = mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("기본값초과");
        assertThat(body).contains("기본값이하");
    }

    @Test
    void 다른_회원의_상품은_개수_합계_품절임박_목록에_포함되지_않는다() throws Exception {
        MockHttpSession otherSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        createProduct(otherSession, "남의상품", "SKU-G", "1", null);

        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("남의상품"))))
                .andExpect(content().string(containsString("상품 수: 0")))
                .andExpect(content().string(containsString("총재고 합계: 0")));
    }
}
