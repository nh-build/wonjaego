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
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantRepository;
import com.wonjaego.product.ProductVariantService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
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

    @Autowired
    private ProductVariantRepository productVariantRepository;

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

    private Long productIdByName(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
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

        // 변형 개수: 티셔츠 4개(블랙/화이트 × S/M) + 가방 1개 = 5개.
        // 총재고 합계: 블랙/S 5(입고 20 - 판매 15) + 가방 9(입고 8 + 반품 1) = 14.
        // 블랙/S 변형은 명시적으로 채워진 품절임박 기준(6) 이하라 대시보드에 뜬다.
        // SKU("TSHIRT-BLACK-S")와 커스텀 기준(6)이 채워진 변형이기도 하다.
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("상품 수: 5")))
                .andExpect(content().string(containsString("총재고 합계: 14")))
                .andExpect(content().string(containsString("베이직 반팔 티셔츠")))
                .andExpect(content().string(containsString("블랙 / S")))
                .andExpect(content().string(containsString("TSHIRT-BLACK-S")));

        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("베이직 반팔 티셔츠")))
                .andExpect(content().string(containsString("레더 크로스백")));

        mockMvc.perform(get("/channels").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("스마트스토어")))
                .andExpect(content().string(containsString("에이블리")))
                .andExpect(content().string(containsString("지그재그")));

        Long tshirtId = productIdByName("베이직 반팔 티셔츠");

        // 옵션이 있는 상품(티셔츠, 색상×사이즈 조합)의 상세 화면: 변형 목록에 SKU·기준이 보이고,
        // Movement 이력에 입고/판매가 함께 보인다.
        mockMvc.perform(get("/products/" + tshirtId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고")))
                .andExpect(content().string(containsString("판매")))
                .andExpect(content().string(containsString("블랙 / S")))
                .andExpect(content().string(containsString("TSHIRT-BLACK-S")));

        Long bagId = productIdByName("레더 크로스백");

        // 옵션이 없는 상품(가방, 단일 변형)의 상세 화면: 입고/반품/교환 이력이 모두 보인다.
        mockMvc.perform(get("/products/" + bagId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고")))
                .andExpect(content().string(containsString("반품")))
                .andExpect(content().string(containsString("교환")));
    }

    @Test
    void 생성된_변형의_최종_재고는_기록된_Movement와_앞뒤가_맞는다() {
        baseInitData().run(new DefaultApplicationArguments());

        Long tshirtId = productIdByName("베이직 반팔 티셔츠");
        List<ProductVariant> tshirtVariants = productVariantRepository.findAllByProductIdWithOptions(tshirtId);

        assertThat(tshirtVariants).hasSize(4);
        ProductVariant blackSmall = tshirtVariants.stream()
                .filter(v -> v.getOptionLabel().equals("블랙 / S"))
                .findFirst()
                .orElseThrow();
        // 20(입고) - 15(판매) = 5
        assertThat(blackSmall.getStockQuantity()).isEqualTo(5);
        assertThat(blackSmall.getSku()).isEqualTo("TSHIRT-BLACK-S");
        assertThat(blackSmall.getLowStockThreshold()).isEqualTo(6);
        assertThat(blackSmall.isLowStock()).isTrue();
        // 나머지 세 조합은 재고 이동이 없어 0으로 남는다.
        assertThat(tshirtVariants).filteredOn(v -> !v.getOptionLabel().equals("블랙 / S"))
                .extracting(ProductVariant::getStockQuantity)
                .containsOnly(0);

        Long bagId = productIdByName("레더 크로스백");
        List<ProductVariant> bagVariants = productVariantRepository.findAllByProductIdWithOptions(bagId);

        assertThat(bagVariants).hasSize(1);
        // 8(입고) + 1(반품) + 0(동일 변형 교환) = 9
        assertThat(bagVariants.get(0).getStockQuantity()).isEqualTo(9);
    }
}
