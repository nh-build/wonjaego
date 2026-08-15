package com.wonjaego.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.channel.SalesChannelRepository;
import com.wonjaego.product.Product;
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
class MovementRecordingTest {

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

    private void recordMovement(MockHttpSession session, Long productId, Long channelId, String type, String quantity, String memo)
            throws Exception {
        mockMvc.perform(post("/movements/new")
                .session(session)
                .with(csrf())
                .param("productId", String.valueOf(productId))
                .param("salesChannelId", String.valueOf(channelId))
                .param("type", type)
                .param("quantity", quantity)
                .param("memo", memo));
    }

    @Test
    void 입고를_기록하면_총재고가_증가한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        Long productId = createProduct(session, "상품A", "SKU-A", "10");
        Long channelId = createChannel(session, "스마트스토어");

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("productId", String.valueOf(productId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "INBOUND")
                        .param("quantity", "5")
                        .param("memo", "정기 입고"))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(15);
    }

    @Test
    void 판매를_기록하면_총재고가_감소한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        Long productId = createProduct(session, "상품B", "SKU-B", "10");
        Long channelId = createChannel(session, "에이블리");

        recordMovement(session, productId, channelId, "SALE", "3", "");

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(7);
    }

    @Test
    void 반품을_기록하면_총재고가_증가한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        Long productId = createProduct(session, "상품C", "SKU-C", "10");
        Long channelId = createChannel(session, "지그재그");

        recordMovement(session, productId, channelId, "RETURN", "2", "");

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(12);
    }

    @Test
    void 재고보다_많은_판매는_거부되고_재고와_기록이_변하지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        Long productId = createProduct(session, "상품D", "SKU-D", "3");
        Long channelId = createChannel(session, "채널4");

        mockMvc.perform(post("/movements/new")
                        .session(session)
                        .with(csrf())
                        .param("productId", String.valueOf(productId))
                        .param("salesChannelId", String.valueOf(channelId))
                        .param("type", "SALE")
                        .param("quantity", "5")
                        .param("memo", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고가 부족합니다")));

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(movementRepository.findAll()).isEmpty();
    }

    @Test
    void 다른_회원_소유_상품을_대상으로_기록하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        Long victimProductId = createProduct(victimSession, "피해자상품", "SKU-V1", "10");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        Long attackerChannelId = createChannel(attackerSession, "공격자채널1");

        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("productId", String.valueOf(victimProductId))
                        .param("salesChannelId", String.valueOf(attackerChannelId))
                        .param("type", "INBOUND")
                        .param("quantity", "1")
                        .param("memo", ""))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(victimProductId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 다른_회원_소유_채널을_대상으로_기록하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        Long victimChannelId = createChannel(victimSession, "피해자채널");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");
        Long attackerProductId = createProduct(attackerSession, "공격자상품", "SKU-A2", "10");

        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("productId", String.valueOf(attackerProductId))
                        .param("salesChannelId", String.valueOf(victimChannelId))
                        .param("type", "INBOUND")
                        .param("quantity", "1")
                        .param("memo", ""))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(attackerProductId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 다른_회원_소유_상품은_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller12", "password123", "가게12");
        Long victimProductId = createProduct(victimSession, "피해자상품2", "SKU-V2", "10");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller13", "password123", "가게13");
        Long attackerChannelId = createChannel(attackerSession, "공격자채널2");

        // quantity=0 fails @Min(1) — ownership must still be enforced first, not skipped.
        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("productId", String.valueOf(victimProductId))
                        .param("salesChannelId", String.valueOf(attackerChannelId))
                        .param("type", "INBOUND")
                        .param("quantity", "0")
                        .param("memo", ""))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(victimProductId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 다른_회원_소유_채널은_입력값이_잘못돼도_404가_반환된다() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller14", "password123", "가게14");
        Long victimChannelId = createChannel(victimSession, "피해자채널2");

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller15", "password123", "가게15");
        Long attackerProductId = createProduct(attackerSession, "공격자상품2", "SKU-A3", "10");

        mockMvc.perform(post("/movements/new")
                        .session(attackerSession)
                        .with(csrf())
                        .param("productId", String.valueOf(attackerProductId))
                        .param("salesChannelId", String.valueOf(victimChannelId))
                        .param("type", "INBOUND")
                        .param("quantity", "0")
                        .param("memo", ""))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(attackerProductId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 상품_상세에서_재고_기록_이력을_확인할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");
        Long productId = createProduct(session, "상품E", "SKU-E", "10");
        Long channelId = createChannel(session, "히스토리채널");

        recordMovement(session, productId, channelId, "INBOUND", "4", "입고 메모");

        mockMvc.perform(get("/products/" + productId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고")))
                .andExpect(content().string(containsString("히스토리채널")))
                .andExpect(content().string(containsString("입고 메모")));
    }

    @Test
    void 재고_기록이_있는_상품은_삭제가_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");
        Long productId = createProduct(session, "상품F", "SKU-F", "10");
        Long channelId = createChannel(session, "채널10");

        recordMovement(session, productId, channelId, "INBOUND", "1", "");

        mockMvc.perform(post("/products/" + productId + "/delete").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고 기록")));

        assertThat(productRepository.findById(productId)).isPresent();
    }

    @Test
    void 재고_기록이_있는_채널은_삭제가_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller11", "password123", "가게11");
        Long productId = createProduct(session, "상품G", "SKU-G", "10");
        Long channelId = createChannel(session, "채널11");

        recordMovement(session, productId, channelId, "INBOUND", "1", "");

        mockMvc.perform(post("/channels/" + channelId + "/delete").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고 기록")));

        assertThat(salesChannelRepository.findById(channelId)).isPresent();
    }
}
