package com.wonjaego.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.product.Product;
import com.wonjaego.product.ProductRepository;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantRepository;
import com.wonjaego.testsupport.AuthTestSupport;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class StockMovementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private MovementRepository movementRepository;

    // Creates a product with two option combos (블랙/화이트) and known initial stock.
    private Map<String, ProductVariant> createProductWithVariants(MockHttpSession session, String name,
                                                                    String blackStock, String whiteStock) throws Exception {
        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", name)
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트")
                        .param("stocksJson", "[" + blackStock + "," + whiteStock + "]"))
                .andExpect(status().is3xxRedirection());

        Product product = productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow();
        return productVariantRepository.findAllByProductIdWithOptions(product.getId()).stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
    }

    private ResultActions postStockIn(MockHttpSession session, Long productId, String type,
                                       Map<Long, Integer> quantities, String memo) throws Exception {
        var request = post("/movements/stock-in").session(session).with(csrf())
                .param("productId", String.valueOf(productId))
                .param("type", type)
                .param("memo", memo);
        int index = 0;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            request.param("entries[" + index + "].variantId", String.valueOf(entry.getKey()));
            request.param("entries[" + index + "].quantity", String.valueOf(entry.getValue()));
            index++;
        }
        return mockMvc.perform(request);
    }

    private ResultActions postStockOut(MockHttpSession session, Long productId, String type,
                                        Map<Long, Integer> quantities, String memo) throws Exception {
        var request = post("/movements/stock-out").session(session).with(csrf())
                .param("productId", String.valueOf(productId))
                .param("type", type)
                .param("memo", memo);
        int index = 0;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            request.param("entries[" + index + "].variantId", String.valueOf(entry.getKey()));
            request.param("entries[" + index + "].quantity", String.valueOf(entry.getValue()));
            index++;
        }
        return mockMvc.perform(request);
    }

    @Test
    void 입고_처리_시_구매입고_사유로_조합별_재고가_증가한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품A", "0", "0");
        Long productId = variants.get("블랙").getProduct().getId();

        postStockIn(session, productId, "INBOUND",
                Map.of(variants.get("블랙").getId(), 10, variants.get("화이트").getId(), 5), "8월 사입분")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/" + productId));

        assertThat(productVariantRepository.findById(variants.get("블랙").getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productVariantRepository.findById(variants.get("화이트").getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void 입고_화면에서_반품과_조정_사유도_사용할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품B", "10", "10");
        Long productId = variants.get("블랙").getProduct().getId();

        postStockIn(session, productId, "RETURN", Map.of(variants.get("블랙").getId(), 2), "")
                .andExpect(status().is3xxRedirection());
        postStockIn(session, productId, "ADJUSTMENT_IN", Map.of(variants.get("화이트").getId(), 3), "실사 조정")
                .andExpect(status().is3xxRedirection());

        assertThat(productVariantRepository.findById(variants.get("블랙").getId()).orElseThrow().getStockQuantity()).isEqualTo(12);
        assertThat(productVariantRepository.findById(variants.get("화이트").getId()).orElseThrow().getStockQuantity()).isEqualTo(13);

        List<Movement> movements = movementRepository.findAllByProductIdWithChannelAndVariant(productId);
        assertThat(movements).anyMatch(m -> m.getType() == MovementType.RETURN);
        assertThat(movements).anyMatch(m -> m.getType() == MovementType.ADJUSTMENT_IN);
    }

    @Test
    void 출고_처리_시_판매_교환출고_폐기_조정_사유로_재고가_감소한다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품C", "20", "20");
        Long productId = variants.get("블랙").getProduct().getId();

        postStockOut(session, productId, "SALE", Map.of(variants.get("블랙").getId(), 3), "")
                .andExpect(status().is3xxRedirection());
        postStockOut(session, productId, "EXCHANGE_OUT", Map.of(variants.get("화이트").getId(), 2), "")
                .andExpect(status().is3xxRedirection());
        postStockOut(session, productId, "DISPOSAL", Map.of(variants.get("블랙").getId(), 1), "불량")
                .andExpect(status().is3xxRedirection());
        postStockOut(session, productId, "ADJUSTMENT_OUT", Map.of(variants.get("화이트").getId(), 4), "실사 조정")
                .andExpect(status().is3xxRedirection());

        assertThat(productVariantRepository.findById(variants.get("블랙").getId()).orElseThrow().getStockQuantity()).isEqualTo(16);
        assertThat(productVariantRepository.findById(variants.get("화이트").getId()).orElseThrow().getStockQuantity()).isEqualTo(14);
    }

    @Test
    void 재고보다_많은_출고는_거부되고_모든_조합의_재고가_변하지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품D", "5", "5");
        Long productId = variants.get("블랙").getProduct().getId();

        // 블랙(5)은 감당 가능하지만 화이트(5)는 초과 — 배치 전체가 거부되어 블랙도 변하지 않아야 한다.
        postStockOut(session, productId, "SALE",
                Map.of(variants.get("블랙").getId(), 3, variants.get("화이트").getId(), 10), "")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고가 부족합니다")));

        assertThat(productVariantRepository.findById(variants.get("블랙").getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(productVariantRepository.findById(variants.get("화이트").getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void 같은_조합이_중복_제출되면_거부되고_재고가_변하지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller13", "password123", "가게13");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품M", "5", "5");
        Long productId = variants.get("블랙").getProduct().getId();
        Long blackId = variants.get("블랙").getId();

        mockMvc.perform(post("/movements/stock-in").session(session).with(csrf())
                        .param("productId", String.valueOf(productId))
                        .param("type", "INBOUND")
                        .param("memo", "")
                        .param("entries[0].variantId", String.valueOf(blackId))
                        .param("entries[0].quantity", "3")
                        .param("entries[1].variantId", String.valueOf(blackId))
                        .param("entries[1].quantity", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("중복된 옵션 조합이 있습니다")));

        assertThat(productVariantRepository.findById(blackId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void 수량을_하나도_입력하지_않으면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품E", "5", "5");
        Long productId = variants.get("블랙").getProduct().getId();

        postStockIn(session, productId, "INBOUND",
                Map.of(variants.get("블랙").getId(), 0, variants.get("화이트").getId(), 0), "")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("수량을 하나 이상 입력해주세요")));
    }

    @Test
    void 입고_화면에서_출고_전용_사유를_쓰면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품F", "5", "5");
        Long productId = variants.get("블랙").getProduct().getId();

        postStockIn(session, productId, "SALE", Map.of(variants.get("블랙").getId(), 1), "")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고 화면에서 사용할 수 없는 사유입니다")));

        assertThat(productVariantRepository.findById(variants.get("블랙").getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void 출고_화면에서_입고_전용_사유를_쓰면_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품G", "5", "5");
        Long productId = variants.get("블랙").getProduct().getId();

        postStockOut(session, productId, "INBOUND", Map.of(variants.get("블랙").getId(), 1), "")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("출고 화면에서 사용할 수 없는 사유입니다")));

        assertThat(productVariantRepository.findById(variants.get("블랙").getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void 다른_회원_소유_상품으로_기록하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");
        Map<String, ProductVariant> victimVariants = createProductWithVariants(victimSession, "피해자상품", "5", "5");
        Long victimProductId = victimVariants.get("블랙").getProduct().getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");

        postStockIn(attackerSession, victimProductId, "INBOUND",
                Map.of(victimVariants.get("블랙").getId(), 1), "")
                .andExpect(status().isNotFound());
    }

    @Test
    void 입고_화면을_상품ID와_함께_열면_폼이_렌더링된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");
        Map<String, ProductVariant> variants = createProductWithVariants(session, "상품H", "5", "5");
        Long productId = variants.get("블랙").getProduct().getId();

        mockMvc.perform(get("/movements/stock-in").param("productId", String.valueOf(productId)).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입고하기")));
    }

    @Test
    void 다른_회원_상품ID로_입고_화면을_열면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller11", "password123", "가게11");
        Map<String, ProductVariant> victimVariants = createProductWithVariants(victimSession, "피해자상품2", "5", "5");
        Long victimProductId = victimVariants.get("블랙").getProduct().getId();

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller12", "password123", "가게12");

        mockMvc.perform(get("/movements/stock-in").param("productId", String.valueOf(victimProductId)).session(attackerSession))
                .andExpect(status().isNotFound());
    }
}
