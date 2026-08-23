package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.movement.Movement;
import com.wonjaego.movement.MovementRepository;
import com.wonjaego.movement.MovementType;
import com.wonjaego.testsupport.AuthTestSupport;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Covers the registration-time "옵션 & 재고" flow's core rule (docs/adr/0006): stock is never
// set directly on a ProductVariant — every non-zero initial stock number entered at
// registration must instead materialize as an INBOUND Movement with no sales channel.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductRegistrationStockTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private MovementRepository movementRepository;

    private Product findByName(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 조합별_재고를_입력하면_조합마다_초기_입고_Movement가_생성된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "옵션재고상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 아이보리")
                        .param("optionGroups[1].name", "사이즈")
                        .param("optionGroups[1].valuesText", "S, M")
                        .param("stocksJson", "[5,0,8,12]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("옵션재고상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());
        Map<String, ProductVariant> byLabel = variants.stream()
                .collect(java.util.stream.Collectors.toMap(ProductVariant::getOptionLabel, v -> v));

        assertThat(byLabel.get("블랙 / S").getStockQuantity()).isEqualTo(5);
        assertThat(byLabel.get("블랙 / M").getStockQuantity()).isEqualTo(0);
        assertThat(byLabel.get("아이보리 / S").getStockQuantity()).isEqualTo(8);
        assertThat(byLabel.get("아이보리 / M").getStockQuantity()).isEqualTo(12);

        List<Movement> blackSMovements = movementRepository.findAllByVariantIdWithChannel(byLabel.get("블랙 / S").getId());
        assertThat(blackSMovements).hasSize(1);
        Movement movement = blackSMovements.get(0);
        assertThat(movement.getType()).isEqualTo(MovementType.INBOUND);
        assertThat(movement.getQuantityChange()).isEqualTo(5);
        assertThat(movement.getSalesChannel()).isNull();
        assertThat(movement.getMemo()).isEqualTo("상품 등록 시 초기 재고");
    }

    @Test
    void 색상_사이즈가_아닌_자유_옵션명도_조합과_초기_입고_Movement가_생성된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "자유옵션상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "기장")
                        .param("optionGroups[0].valuesText", "롱, 미디")
                        .param("stocksJson", "[5,8]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("자유옵션상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());
        Map<String, ProductVariant> byLabel = variants.stream()
                .collect(java.util.stream.Collectors.toMap(ProductVariant::getOptionLabel, v -> v));

        assertThat(byLabel.get("롱").getStockQuantity()).isEqualTo(5);
        assertThat(byLabel.get("미디").getStockQuantity()).isEqualTo(8);
        assertThat(movementRepository.findAllByVariantIdWithChannel(byLabel.get("롱").getId())).hasSize(1);
    }

    @Test
    void 재고가_0인_조합은_Movement_없이_0으로_유지된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "일부품절상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙")
                        .param("optionGroups[1].name", "사이즈")
                        .param("optionGroups[1].valuesText", "S, M")
                        .param("stocksJson", "[3,0]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("일부품절상품");
        ProductVariant zeroStockVariant = productVariantRepository.findAllByProductIdWithOptions(product.getId()).stream()
                .filter(v -> v.getOptionLabel().equals("블랙 / M"))
                .findFirst()
                .orElseThrow();

        assertThat(zeroStockVariant.getStockQuantity()).isEqualTo(0);
        assertThat(movementRepository.findAllByVariantIdWithChannel(zeroStockVariant.getId())).isEmpty();
    }

    @Test
    void 옵션_없이_stocksJson_하나로_단일_변형_재고를_등록할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "단일재고상품")
                        .param("price", "10000")
                        .param("stocksJson", "[7]"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("단일재고상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());

        assertThat(variants).hasSize(1);
        assertThat(variants.get(0).getStockQuantity()).isEqualTo(7);
        assertThat(movementRepository.findAllByVariantIdWithChannel(variants.get(0).getId())).hasSize(1);
    }

    @Test
    void stocksJson이_비어있으면_모든_조합이_재고_0으로_생성되고_Movement가_없다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "재고미입력상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 화이트"))
                .andExpect(status().is3xxRedirection());

        Product product = findByName("재고미입력상품");
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());

        assertThat(variants).hasSize(2);
        variants.forEach(variant -> {
            assertThat(variant.getStockQuantity()).isEqualTo(0);
            assertThat(movementRepository.findAllByVariantIdWithChannel(variant.getId())).isEmpty();
        });
    }

    @Test
    void 재고_입력_개수가_조합_개수와_다르면_등록이_거부되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "개수불일치상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "색상")
                        .param("optionGroups[0].valuesText", "블랙, 아이보리")
                        .param("optionGroups[1].name", "사이즈")
                        .param("optionGroups[1].valuesText", "S, M")
                        .param("stocksJson", "[5,0,8]"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고 입력 개수가 옵션 조합 개수와 일치하지 않습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("개수불일치상품"))).isFalse();
    }

    @Test
    void 재고_데이터가_올바른_JSON이_아니면_등록이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "잘못된JSON상품")
                        .param("price", "10000")
                        .param("stocksJson", "not-json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고 데이터 형식이 올바르지 않습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("잘못된JSON상품"))).isFalse();
    }

    @Test
    void 옵션_조합이_너무_많으면_등록이_거부되고_아무것도_저장되지_않는다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");
        String manyValues = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "값" + i)
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "조합폭발상품")
                        .param("price", "10000")
                        .param("optionGroups[0].name", "축1")
                        .param("optionGroups[0].valuesText", manyValues)
                        .param("optionGroups[1].name", "축2")
                        .param("optionGroups[1].valuesText", manyValues))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("옵션 조합이 너무 많습니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("조합폭발상품"))).isFalse();
    }

    @Test
    void 재고에_음수가_있으면_등록이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");

        mockMvc.perform(post("/products")
                        .session(session).with(csrf())
                        .param("name", "음수재고상품")
                        .param("price", "10000")
                        .param("stocksJson", "[-1]"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("재고는 0 이상의 숫자여야 합니다")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("음수재고상품"))).isFalse();
    }
}
