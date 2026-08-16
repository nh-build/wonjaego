package com.wonjaego.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.channel.SalesChannelRepository;
import com.wonjaego.product.ProductRepository;
import com.wonjaego.product.ProductVariantRepository;
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
class ExchangeMovementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private SalesChannelRepository salesChannelRepository;

    @Autowired
    private MovementRepository movementRepository;

    // Creates a product with no option groups, so it has exactly one (no-option) variant,
    // stocked via a bootstrap channel named after the product (unique per call, so this is
    // safe to call for multiple different members within the same test without name clashes).
    private Long createVariant(MockHttpSession session, String name, String stockQuantity) throws Exception {
        mockMvc.perform(post("/products")
                .session(session)
                .with(csrf())
                .param("name", name)
                .param("price", "1000"));
        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
        Long variantId = productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();

        if (!stockQuantity.equals("0")) {
            Long bootstrapChannelId = createChannel(session, "입고용_" + name);
            mockMvc.perform(post("/movements/new")
                    .session(session).with(csrf())
                    .param("variantId", String.valueOf(variantId))
                    .param("salesChannelId", String.valueOf(bootstrapChannelId))
                    .param("type", "INBOUND")
                    .param("quantity", stockQuantity)
                    .param("memo", ""));
        }
        return variantId;
    }

    private Long createChannel(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/channels")
                .session(session)
                .with(csrf())
                .param("name", name));
        return salesChannelRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void 새_상품을_비우면_동일_변형_교환으로_처리되어_재고_변동_없이_이력만_남는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        Long variantId = createVariant(session, "상품A", "10");
        Long channelId = createChannel(session, "채널1");

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("variantId", String.valueOf(variantId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "1")
                        .param("memo", "사이즈 재발송")
                        .param("newVariantId", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(productVariantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(movementRepository.findAll().stream().filter(m -> m.getType() == MovementType.EXCHANGE).count())
                .isEqualTo(1);
    }

    @Test
    void 다른_변형으로_교환하면_원_변형은_증가하고_새_변형은_감소한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        Long originalId = createVariant(session, "상품B", "10");
        Long newId = createVariant(session, "상품C", "5");
        Long channelId = createChannel(session, "채널2");

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("variantId", String.valueOf(originalId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "3")
                        .param("memo", "")
                        .param("newVariantId", String.valueOf(newId)))
                .andExpect(status().is3xxRedirection());

        assertThat(productVariantRepository.findById(originalId).orElseThrow().getStockQuantity()).isEqualTo(13);
        assertThat(productVariantRepository.findById(newId).orElseThrow().getStockQuantity()).isEqualTo(2);

        assertThat(movementRepository.findAll().stream().filter(m -> m.getType() == MovementType.EXCHANGE).count())
                .isEqualTo(2);
    }

    @Test
    void 새_변형_재고가_부족하면_두_Movement_모두_저장되지_않고_원_변형도_롤백된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        Long originalId = createVariant(session, "상품D", "10");
        Long newId = createVariant(session, "상품E", "2");
        Long channelId = createChannel(session, "채널3");

        long exchangeCountBefore = movementRepository.findAll().stream()
                .filter(m -> m.getType() == MovementType.EXCHANGE).count();

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("variantId", String.valueOf(originalId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "5")
                        .param("memo", "")
                        .param("newVariantId", String.valueOf(newId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고가 부족합니다")));

        assertThat(productVariantRepository.findById(originalId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productVariantRepository.findById(newId).orElseThrow().getStockQuantity()).isEqualTo(2);
        assertThat(movementRepository.findAll().stream().filter(m -> m.getType() == MovementType.EXCHANGE).count())
                .isEqualTo(exchangeCountBefore);
    }

    @Test
    void 원_변형이_다른_회원_소유면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        Long victimVariantId = createVariant(victimSession, "피해자상품", "10");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        Long attackerChannelId = createChannel(attackerSession, "공격자채널1");

        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("variantId", String.valueOf(victimVariantId))
                        .param("salesChannelId", String.valueOf(attackerChannelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "1")
                        .param("memo", "")
                        .param("newVariantId", ""))
                .andExpect(status().isNotFound());
    }

    @Test
    void 새_변형이_다른_회원_소유면_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        Long attackerVariantId = createVariant(attackerSession, "공격자상품", "10");
        Long attackerChannelId = createChannel(attackerSession, "공격자채널2");

        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        Long victimVariantId = createVariant(victimSession, "피해자상품2", "10");

        // quantity=0 fails @Min(1) — ownership of newVariantId must still be enforced first.
        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("variantId", String.valueOf(attackerVariantId))
                        .param("salesChannelId", String.valueOf(attackerChannelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "0")
                        .param("memo", "")
                        .param("newVariantId", String.valueOf(victimVariantId)))
                .andExpect(status().isNotFound());

        assertThat(productVariantRepository.findById(attackerVariantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productVariantRepository.findById(victimVariantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 상품_상세에서_교환_기록도_다른_타입과_함께_시간순으로_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");
        Long variantId = createVariant(session, "상품F", "10");
        Long channelId = createChannel(session, "채널8");
        Long productId = productVariantRepository.findById(variantId).orElseThrow().getProduct().getId();

        mockMvc.perform(post("/movements/new")
                .session(session).with(csrf())
                .param("variantId", String.valueOf(variantId))
                .param("salesChannelId", String.valueOf(channelId))
                .param("type", "EXCHANGE")
                .param("quantity", "1")
                .param("memo", "재발송")
                .param("newVariantId", ""));

        String body = mockMvc.perform(get("/products/" + productId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고")))
                .andExpect(content().string(containsString("교환")))
                .andExpect(content().string(containsString("재발송")))
                .andReturn().getResponse().getContentAsString();

        // 더 최근에 기록된 EXCHANGE(메모 "재발송")가 createVariant가 기록한 최초 입고(채널명 "입고용_상품F")보다
        // 먼저(위쪽에) 렌더링돼야 한다.
        assertThat(body.indexOf("재발송")).isLessThan(body.indexOf("입고용_상품F"));
    }
}
