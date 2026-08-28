package com.wonjaego.integration.zigzag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wonjaego.channel.ChannelCredentialService;
import com.wonjaego.channel.ChannelType;
import com.wonjaego.channel.SalesChannelRepository;
import com.wonjaego.member.Member;
import com.wonjaego.member.MemberService;
import com.wonjaego.movement.MovementRepository;
import com.wonjaego.movement.MovementType;
import com.wonjaego.product.Product;
import com.wonjaego.product.ProductRepository;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantRepository;
import com.wonjaego.testsupport.FakeZigzagOpenApiClient;
import com.wonjaego.testsupport.StubZigzagOpenApiClientConfig;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Import(StubZigzagOpenApiClientConfig.class)
@Transactional
class ZigzagProductImportServiceTest {

    // sales_price/original_price deliberately differ on each item's site_list — the mapping
    // must use original_price (sales_price is confirmed deprecated/unused in the real Open
    // API schema even though its name suggests otherwise), so a test asserting the wrong
    // value would only pass if that mapping were wrong.
    private static final String ONE_PRODUCT_TWO_ITEMS = """
            {
              "product_list": {
                "item_list": [
                  {
                    "id": "P1",
                    "name": "린넨 원피스",
                    "sales_status": "SALE",
                    "display_status": "DISPLAY",
                    "image_list": [ { "image_url": "https://cdn.zigzag.kr/P1.jpg", "image_type": "MAIN" } ],
                    "site_list": [ { "site": "ZIGZAG", "country": "KOR", "original_price": 39000, "discount_price": 35000 } ],
                    "option_list": [
                      { "name": "색상", "value_list": [ { "value": "블랙" } ] },
                      { "name": "사이즈", "value_list": [ { "value": "S" }, { "value": "M" } ] }
                    ],
                    "item_list": [
                      { "id": "I1", "name": "블랙/S", "item_code": "C1", "sales_status": "SALE",
                        "attribute_list": [{"name":"색상","value":"블랙"},{"name":"사이즈","value":"S"}],
                        "inventory": {"quantity": 10},
                        "site_list": [ { "site": "ZIGZAG", "country": "KOR", "sales_price": 29000, "original_price": 32000 } ] },
                      { "id": "I2", "name": "블랙/M", "item_code": "C2", "sales_status": "SALE",
                        "attribute_list": [{"name":"색상","value":"블랙"},{"name":"사이즈","value":"M"}],
                        "inventory": {"quantity": 5},
                        "site_list": [ { "site": "ZIGZAG", "country": "KOR", "sales_price": 29000, "original_price": 35000 } ] }
                    ]
                  }
                ]
              }
            }
            """;

    @Autowired
    private ZigzagProductImportService zigzagProductImportService;

    @Autowired
    private ZigzagOpenApiClient zigzagOpenApiClient;

    @Autowired
    private ChannelCredentialService channelCredentialService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private SalesChannelRepository salesChannelRepository;

    @Autowired
    private MovementRepository movementRepository;

    private FakeZigzagOpenApiClient fakeClient() {
        return (FakeZigzagOpenApiClient) zigzagOpenApiClient;
    }

    private Long createMemberWithZigzagKeys(String username) {
        Member member = memberService.signUp(username, "password123", "가게-" + username);
        channelCredentialService.connect(member.getId(), ChannelType.ZIGZAG, "access", "secret");
        return member.getId();
    }

    @Test
    void 처음_가져오면_상품과_조합이_생성되고_INBOUND로_초기재고가_채워진다() {
        Long memberId = createMemberWithZigzagKeys("zimport1");
        fakeClient().respondWith(ONE_PRODUCT_TWO_ITEMS);

        ZigzagImportResult result = zigzagProductImportService.importProducts(memberId);

        assertThat(result.productCount()).isEqualTo(1);
        Product product = productRepository.findAllByMemberId(memberId).get(0);
        assertThat(product.getName()).isEqualTo("린넨 원피스");
        assertThat(product.getExternalChannelType()).isEqualTo(ChannelType.ZIGZAG);
        assertThat(product.getExternalProductId()).isEqualTo("P1");
        assertThat(product.getExternalImageUrl()).isEqualTo("https://cdn.zigzag.kr/P1.jpg");

        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());
        Map<String, ProductVariant> byLabel = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
        assertThat(byLabel.get("블랙 / S").getStockQuantity()).isEqualTo(10);
        assertThat(byLabel.get("블랙 / M").getStockQuantity()).isEqualTo(5);
        // original_price, not sales_price (deprecated/unused in the real Open API).
        assertThat(byLabel.get("블랙 / S").getPrice()).isEqualByComparingTo("32000");
        assertThat(byLabel.get("블랙 / M").getPrice()).isEqualByComparingTo("35000");

        assertThat(salesChannelRepository.findByMemberIdAndName(memberId, "지그재그")).isPresent();
        List<com.wonjaego.movement.Movement> movements =
                movementRepository.findAllByProductIdWithChannelAndVariant(product.getId());
        assertThat(movements).hasSize(2);
        assertThat(movements).allMatch(m -> m.getType() == MovementType.INBOUND);
        assertThat(movements).allMatch(m -> m.getSalesChannel() != null && "지그재그".equals(m.getSalesChannel().getName()));
    }

    @Test
    void 다시_가져오면_같은_상품을_중복_생성하지_않고_재고와_가격_차이만큼만_조정한다() {
        Long memberId = createMemberWithZigzagKeys("zimport2");
        fakeClient().respondWith(ONE_PRODUCT_TWO_ITEMS);
        zigzagProductImportService.importProducts(memberId);

        String updatedResponse = ONE_PRODUCT_TWO_ITEMS
                .replace("\"quantity\": 10", "\"quantity\": 15")
                .replace("\"original_price\": 32000", "\"original_price\": 36000");
        fakeClient().respondWith(updatedResponse);
        ZigzagImportResult secondResult = zigzagProductImportService.importProducts(memberId);

        assertThat(secondResult.productCount()).isEqualTo(1);
        assertThat(productRepository.findAllByMemberId(memberId)).hasSize(1);

        Product product = productRepository.findAllByMemberId(memberId).get(0);
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdWithOptions(product.getId());
        assertThat(variants).hasSize(2);
        Map<String, ProductVariant> byLabel = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getOptionLabel, v -> v));
        assertThat(byLabel.get("블랙 / S").getStockQuantity()).isEqualTo(15);
        assertThat(byLabel.get("블랙 / M").getStockQuantity()).isEqualTo(5);
        // Re-import refreshes price directly (no Movement involved — price isn't stock).
        assertThat(byLabel.get("블랙 / S").getPrice()).isEqualByComparingTo("36000");

        List<com.wonjaego.movement.Movement> movements =
                movementRepository.findAllByProductIdWithChannelAndVariant(product.getId());
        // 2 INBOUND from the first import + 1 ADJUSTMENT_IN for the +5 reconciliation
        // (블랙/M's quantity didn't change, so no second movement for it).
        assertThat(movements).hasSize(3);
        assertThat(movements).anyMatch(m -> m.getType() == MovementType.ADJUSTMENT_IN && m.getQuantityChange() == 5);
    }

    @Test
    void 지그재그_키를_연동하지_않은_회원은_가져오기가_거부된다() {
        Member member = memberService.signUp("zimport3", "password123", "가게-zimport3");

        assertThrows(com.wonjaego.channel.ChannelCredentialNotFoundException.class,
                () -> zigzagProductImportService.importProducts(member.getId()));
    }

    @Test
    void API_에러가_나면_예외가_그대로_전달되고_아무것도_저장되지_않는다() {
        Long memberId = createMemberWithZigzagKeys("zimport4");
        fakeClient().failWith(FakeZigzagOpenApiClient.authError());

        assertThrows(ZigzagApiException.class, () -> zigzagProductImportService.importProducts(memberId));

        assertThat(productRepository.findAllByMemberId(memberId)).isEmpty();
    }
}
