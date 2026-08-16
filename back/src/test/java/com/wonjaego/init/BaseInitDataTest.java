package com.wonjaego.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.member.MemberService;
import com.wonjaego.movement.MovementService;
import com.wonjaego.product.ProductRepository;
import com.wonjaego.product.ProductService;
import com.wonjaego.product.ProductVariantService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class BaseInitDataTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private SalesChannelService salesChannelService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService productVariantService;

    @Autowired
    private MovementService movementService;

    @Autowired
    private ProductRepository productRepository;

    // @Profile("dev") means the bean is never registered under the test profile — construct
    // and invoke run() directly to exercise its seeding logic against the isolated test H2.
    // This is a plain `new`, not the Spring-managed proxy, so BaseInitData's own @Transactional
    // isn't exercised here — the ambient transaction from this test class's own @Transactional
    // covers these tests instead. Rollback-on-partial-failure of the real production proxy is
    // not covered by this test class.
    private BaseInitData baseInitData() {
        return new BaseInitData(memberRepository, memberService, salesChannelService, productService,
                productVariantService, movementService);
    }

    private MockHttpSession loginAsSampleMember() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "seller")
                        .param("password", "password123"))
                .andReturn();
        HttpSession session = result.getRequest().getSession(false);
        return (MockHttpSession) session;
    }

    @Test
    void test_프로필에서는_BaseInitData_빈이_등록되지_않는다() {
        assertThatThrownBy(() -> applicationContext.getBean(BaseInitData.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void 이미_Member가_있으면_다시_실행해도_중복_생성되지_않는다() {
        baseInitData().run(new DefaultApplicationArguments());
        baseInitData().run(new DefaultApplicationArguments());

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    void 샘플_데이터_생성_후_로그인하면_대시보드_상품_채널_이력에서_확인된다() throws Exception {
        baseInitData().run(new DefaultApplicationArguments());

        MockHttpSession session = loginAsSampleMember();

        // 블랙/S 변형은 20(입고) - 15(판매) = 5, 기본 품절임박 기준(5) 이하라 대시보드에 뜬다.
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("베이직 반팔 티셔츠")))
                .andExpect(content().string(containsString("블랙 / S")));

        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("베이직 반팔 티셔츠")))
                .andExpect(content().string(containsString("레더 크로스백")));

        mockMvc.perform(get("/channels").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("스마트스토어")))
                .andExpect(content().string(containsString("에이블리")))
                .andExpect(content().string(containsString("지그재그")));

        Long tshirtId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("베이직 반팔 티셔츠"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/products/" + tshirtId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고")))
                .andExpect(content().string(containsString("판매")))
                .andExpect(content().string(containsString("블랙 / S")));
    }
}
