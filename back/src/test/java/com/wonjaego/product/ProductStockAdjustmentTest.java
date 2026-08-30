package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.movement.MovementRepository;
import com.wonjaego.movement.MovementType;
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

// Covers the product-detail screen's stepper/direct-entry "변경사항 저장" bar
// (POST /products/{id}/stock-adjustments) — MovementService.recordQuickAdjustments().
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductStockAdjustmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private MovementRepository movementRepository;

    private Long createProductWithStock(MockHttpSession session, String name, int initialStock) throws Exception {
        mockMvc.perform(post("/products")
                .session(session).with(csrf())
                .param("name", name)
                .param("price", "1000")
                .param("stocksJson", "[" + initialStock + "]"));
        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().orElseThrow().getId();
        return productId;
    }

    private Long firstVariantId(Long productId) {
        return productVariantRepository.findAllByProductIdWithOptions(productId).get(0).getId();
    }

    @Test
    void 스테퍼로_증가시키면_INBOUND로_기록된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller1", "password123", "가게1");
        Long productId = createProductWithStock(session, "조정상품1", 10);
        Long variantId = firstVariantId(productId);

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"variantId\":" + variantId + ",\"type\":\"INBOUND\",\"quantity\":3}]}"))
                .andExpect(status().isOk());

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getStockQuantity()).isEqualTo(13);
        assertThat(movementRepository.findAllByVariantIdWithChannel(variantId))
                .anyMatch(m -> m.getType() == MovementType.INBOUND && m.getQuantityChange() == 3);
    }

    @Test
    void 스테퍼로_감소시키면_ADJUSTMENT_OUT으로_기록된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller2", "password123", "가게2");
        Long productId = createProductWithStock(session, "조정상품2", 10);
        Long variantId = firstVariantId(productId);

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"variantId\":" + variantId + ",\"type\":\"ADJUSTMENT_OUT\",\"quantity\":4}]}"))
                .andExpect(status().isOk());

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getStockQuantity()).isEqualTo(6);
        assertThat(movementRepository.findAllByVariantIdWithChannel(variantId))
                .anyMatch(m -> m.getType() == MovementType.ADJUSTMENT_OUT && m.getQuantityChange() == -4);
    }

    @Test
    void 직접입력으로_늘리면_ADJUSTMENT_IN으로_기록된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller3", "password123", "가게3");
        Long productId = createProductWithStock(session, "조정상품3", 10);
        Long variantId = firstVariantId(productId);

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"variantId\":" + variantId + ",\"type\":\"ADJUSTMENT_IN\",\"quantity\":20}]}"))
                .andExpect(status().isOk());

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getStockQuantity()).isEqualTo(30);
        assertThat(movementRepository.findAllByVariantIdWithChannel(variantId))
                .anyMatch(m -> m.getType() == MovementType.ADJUSTMENT_IN && m.getQuantityChange() == 20);
    }

    @Test
    void 재고보다_많이_줄이면_거부되고_아무것도_바뀌지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller4", "password123", "가게4");
        Long productId = createProductWithStock(session, "조정상품4", 3);
        Long variantId = firstVariantId(productId);
        // Registering with initialStock=3 already recorded one INBOUND movement (ADR 0006) —
        // capture that baseline count so the assertion below checks "no new movement", not
        // "zero movements ever".
        int movementCountBeforeAttempt = movementRepository.findAllByVariantIdWithChannel(variantId).size();

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"variantId\":" + variantId + ",\"type\":\"ADJUSTMENT_OUT\",\"quantity\":10}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        assertThat(productVariantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(movementRepository.findAllByVariantIdWithChannel(variantId)).hasSize(movementCountBeforeAttempt);
    }

    @Test
    void 허용되지_않는_사유는_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller5", "password123", "가게5");
        Long productId = createProductWithStock(session, "조정상품5", 10);
        Long variantId = firstVariantId(productId);

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"variantId\":" + variantId + ",\"type\":\"SALE\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());

        assertThat(productVariantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 다른_상품의_변형_id는_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller6", "password123", "가게6");
        Long productId = createProductWithStock(session, "조정상품6", 10);
        Long otherProductId = createProductWithStock(session, "다른상품6", 10);
        Long otherVariantId = firstVariantId(otherProductId);

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"variantId\":" + otherVariantId + ",\"type\":\"INBOUND\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());

        assertThat(productVariantRepository.findById(otherVariantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void 다른_회원_소유_상품_id로_요청하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller7", "password123", "가게7");
        Long productId = createProductWithStock(victimSession, "피해자조정상품", 10);
        Long variantId = firstVariantId(productId);

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller8", "password123", "가게8");

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(attackerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"variantId\":" + variantId + ",\"type\":\"INBOUND\",\"quantity\":1}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 빈_변경사항은_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "adjustseller9", "password123", "가게9");
        Long productId = createProductWithStock(session, "조정상품9", 10);

        mockMvc.perform(post("/products/" + productId + "/stock-adjustments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
