package com.wonjaego.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.product.ProductRepository;
import com.wonjaego.testsupport.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 설정 화면의 "재고 임박 기준" 값이 홈 "재고 부족" 타일, 상품목록 "재고부족" 필터, 재고 배지
// 색상 판단에 실제로 반영되는지 — 값을 바꿔도 아무 데도 안 먹히는 회귀를 잡기 위한 핵심 경로.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class LowStockThresholdSettingsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long createProductWithStock(MockHttpSession session, String name, int initialStock) throws Exception {
        mockMvc.perform(post("/products")
                .session(session).with(csrf())
                .param("name", name)
                .param("price", "1000")
                .param("stocksJson", "[" + initialStock + "]"));
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void 설정_화면은_기본값_5를_보여준다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "settingsseller1", "password123", "가게1");

        mockMvc.perform(get("/settings").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("lowStockThreshold", 5));
    }

    @Test
    void 재고_임박_기준을_바꾸면_홈_상품목록_배지가_모두_새_기준을_따른다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "settingsseller2", "password123", "가게2");
        createProductWithStock(session, "재고4개상품", 4);

        // 기본 기준 5 — 4개는 재고부족(4<=5)으로 표시된다.
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("lowStockVariantCount", 1L));
        mockMvc.perform(get("/products").param("stock", "low").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고4개상품")));

        mockMvc.perform(post("/settings/low-stock-threshold")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":3}"))
                .andExpect(status().isOk());

        // 기준을 3으로 낮추면 4개는 더 이상 재고부족이 아니다(4>3).
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("lowStockVariantCount", 0L));
        mockMvc.perform(get("/products").param("stock", "low").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("재고4개상품"))));
        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("임박"))));
    }

    @Test
    void 음수_기준값은_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "settingsseller3", "password123", "가게3");

        mockMvc.perform(post("/settings/low-stock-threshold")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":-1}"))
                .andExpect(status().isBadRequest());

        Long memberId = memberRepository.findByUsername("settingsseller3").orElseThrow().getId();
        assertThat(memberRepository.findById(memberId).orElseThrow().getLowStockThreshold()).isEqualTo(5);
    }

    @Test
    void 더보기_화면은_프로필과_메뉴를_보여준다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "settingsseller4", "password123", "가게4");

        mockMvc.perform(get("/more").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("가게4")))
                .andExpect(content().string(containsString("판매채널 연동")))
                .andExpect(content().string(containsString("로그아웃")));
    }

    @Test
    void 재고_탭은_입출고_버튼과_이력을_보여준다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "settingsseller5", "password123", "가게5");
        createProductWithStock(session, "이력상품", 10);

        mockMvc.perform(get("/movements").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고하기")))
                .andExpect(content().string(containsString("출고하기")))
                .andExpect(content().string(containsString("이력상품")));
    }
}
