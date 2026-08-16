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
    private SalesChannelRepository salesChannelRepository;

    @Autowired
    private MovementRepository movementRepository;

    private Long createProduct(MockHttpSession session, String name, String sku, String stockQuantity) throws Exception {
        mockMvc.perform(post("/products")
                .session(session)
                .with(csrf())
                .param("name", name)
                .param("sku", sku)
                .param("price", "1000")
                .param("stockQuantity", stockQuantity));
        return productRepository.findAll().stream()
                .filter(p -> p.getSku().equals(sku))
                .findFirst()
                .orElseThrow()
                .getId();
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
    void 새_상품을_비우면_동일_상품_교환으로_처리되어_재고_변동_없이_이력만_남는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        Long productId = createProduct(session, "상품A", "SKU-A", "10");
        Long channelId = createChannel(session, "채널1");

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("productId", String.valueOf(productId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "1")
                        .param("memo", "사이즈 재발송")
                        .param("newProductId", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(movementRepository.findAll()).hasSize(1);
        assertThat(movementRepository.findAll().get(0).getQuantityChange()).isEqualTo(0);
        assertThat(movementRepository.findAll().get(0).getType()).isEqualTo(MovementType.EXCHANGE);
    }

    @Test
    void 다른_상품으로_교환하면_원_상품은_증가하고_새_상품은_감소한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        Long originalId = createProduct(session, "상품B", "SKU-B", "10");
        Long newId = createProduct(session, "상품C", "SKU-C", "5");
        Long channelId = createChannel(session, "채널2");

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("productId", String.valueOf(originalId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "3")
                        .param("memo", "")
                        .param("newProductId", String.valueOf(newId)))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(originalId).orElseThrow().getStockQuantity()).isEqualTo(13);
        assertThat(productRepository.findById(newId).orElseThrow().getStockQuantity()).isEqualTo(2);

        assertThat(movementRepository.findAll().stream().filter(m -> m.getType() == MovementType.EXCHANGE).count())
                .isEqualTo(2);
    }

    @Test
    void 새_상품_재고가_부족하면_두_Movement_모두_저장되지_않고_원_상품도_롤백된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        Long originalId = createProduct(session, "상품D", "SKU-D", "10");
        Long newId = createProduct(session, "상품E", "SKU-E", "2");
        Long channelId = createChannel(session, "채널3");

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("productId", String.valueOf(originalId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "5")
                        .param("memo", "")
                        .param("newProductId", String.valueOf(newId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고가 부족합니다")));

        assertThat(productRepository.findById(originalId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productRepository.findById(newId).orElseThrow().getStockQuantity()).isEqualTo(2);
        assertThat(movementRepository.findAll()).isEmpty();
    }

    @Test
    void 원_상품이_다른_회원_소유면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        Long victimProductId = createProduct(victimSession, "피해자상품", "SKU-V1", "10");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        Long attackerChannelId = createChannel(attackerSession, "공격자채널1");

        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("productId", String.valueOf(victimProductId))
                        .param("salesChannelId", String.valueOf(attackerChannelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "1")
                        .param("memo", "")
                        .param("newProductId", ""))
                .andExpect(status().isNotFound());
    }

    @Test
    void 새_상품이_다른_회원_소유면_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        Long attackerProductId = createProduct(attackerSession, "공격자상품", "SKU-A2", "10");
        Long attackerChannelId = createChannel(attackerSession, "공격자채널2");

        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        Long victimProductId = createProduct(victimSession, "피해자상품2", "SKU-V2", "10");

        // quantity=0 fails @Min(1) — ownership of newProductId must still be enforced first.
        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("productId", String.valueOf(attackerProductId))
                        .param("salesChannelId", String.valueOf(attackerChannelId))
                        .param("type", "EXCHANGE")
                        .param("quantity", "0")
                        .param("memo", "")
                        .param("newProductId", String.valueOf(victimProductId)))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(attackerProductId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productRepository.findById(victimProductId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 상품_상세에서_교환_기록도_다른_타입과_함께_시간순으로_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");
        Long productId = createProduct(session, "상품F", "SKU-F", "10");
        Long channelId = createChannel(session, "채널8");

        mockMvc.perform(post("/movements/new")
                .session(session).with(csrf())
                .param("productId", String.valueOf(productId))
                .param("salesChannelId", String.valueOf(channelId))
                .param("type", "INBOUND")
                .param("quantity", "5")
                .param("memo", "입고"));

        mockMvc.perform(post("/movements/new")
                .session(session).with(csrf())
                .param("productId", String.valueOf(productId))
                .param("salesChannelId", String.valueOf(channelId))
                .param("type", "EXCHANGE")
                .param("quantity", "1")
                .param("memo", "재발송")
                .param("newProductId", ""));

        String body = mockMvc.perform(get("/products/" + productId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고")))
                .andExpect(content().string(containsString("교환")))
                .andExpect(content().string(containsString("재발송")))
                .andReturn().getResponse().getContentAsString();

        // 더 최근에 기록된 EXCHANGE(메모 "재발송")가 INBOUND(메모 "입고")보다 먼저(위쪽에) 렌더링돼야 한다.
        assertThat(body.indexOf("재발송")).isLessThan(body.indexOf("입고"));
    }
}
