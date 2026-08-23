package com.wonjaego.integration.zigzag;

import com.wonjaego.channel.ChannelCredentialService;
import com.wonjaego.channel.ChannelType;
import com.wonjaego.channel.DecryptedCredential;
import com.wonjaego.channel.SalesChannel;
import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.movement.MovementService;
import com.wonjaego.movement.MovementType;
import com.wonjaego.product.OptionGroup;
import com.wonjaego.product.OptionGroupRepository;
import com.wonjaego.product.OptionValue;
import com.wonjaego.product.OptionValueRepository;
import com.wonjaego.product.Product;
import com.wonjaego.product.ProductRepository;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// Imports a member's Zigzag products/옵션/재고 into wonjaego's own Product/ProductVariant model.
// Every stock change still goes through MovementService (ADR 0001) — never a direct set.
// A previously-imported product/variant (matched by Zigzag's own id, see Product/ProductVariant
// external key fields) is updated in place rather than duplicated; its stock is reconciled to
// the freshly-fetched quantity via an ADJUSTMENT movement instead of a raw INBOUND, so re-running
// an import doesn't double-count stock already recorded on a previous run (ADR 0007).
@Slf4j
@Service
public class ZigzagProductImportService {

    private static final String CHANNEL_NAME = "지그재그";
    private static final String NEW_VARIANT_MEMO = "지그재그 연동으로 가져온 초기재고";
    private static final String SYNC_MEMO = "지그재그 재고 동기화";

    // item_list appears at two nesting levels here: ProductList.item_list (products) and
    // Product.item_list (그 상품의 품목/옵션조합). attribute_list carries the option name/value
    // pair per 품목 (Item), inventory.quantity is its 가용 재고.
    private static final String PRODUCT_LIST_QUERY = """
            query {
              product_list {
                item_list {
                  id
                  name
                  price { original_price }
                  item_list {
                    id
                    attribute_list { name value }
                    inventory { quantity }
                  }
                }
              }
            }
            """;

    private final ZigzagOpenApiClient zigzagOpenApiClient;
    private final ChannelCredentialService channelCredentialService;
    private final SalesChannelService salesChannelService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionValueRepository optionValueRepository;
    private final MemberRepository memberRepository;
    private final MovementService movementService;
    private final String baseUrl;

    public ZigzagProductImportService(ZigzagOpenApiClient zigzagOpenApiClient,
                                       ChannelCredentialService channelCredentialService,
                                       SalesChannelService salesChannelService,
                                       ProductRepository productRepository,
                                       ProductVariantRepository productVariantRepository,
                                       OptionGroupRepository optionGroupRepository,
                                       OptionValueRepository optionValueRepository,
                                       MemberRepository memberRepository,
                                       MovementService movementService,
                                       @Value("${wonjaego.zigzag.base-url}") String baseUrl) {
        this.zigzagOpenApiClient = zigzagOpenApiClient;
        this.channelCredentialService = channelCredentialService;
        this.salesChannelService = salesChannelService;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionValueRepository = optionValueRepository;
        this.memberRepository = memberRepository;
        this.movementService = movementService;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public ZigzagImportResult importProducts(Long memberId) {
        DecryptedCredential credential = channelCredentialService.getDecrypted(memberId, ChannelType.ZIGZAG);
        JsonNode data = zigzagOpenApiClient.query(credential.accessKey(), credential.secretKey(), baseUrl, PRODUCT_LIST_QUERY);

        SalesChannel channel = salesChannelService.findOrCreateByName(memberId, CHANNEL_NAME);
        Member member = memberRepository.getReferenceById(memberId);

        JsonNode zigzagProducts = data.path("product_list").path("item_list");
        int productCount = 0;
        for (JsonNode zigzagProduct : zigzagProducts) {
            importProduct(memberId, member, channel, zigzagProduct);
            productCount++;
        }
        log.info("지그재그 상품 {}건 가져오기 완료. memberId={}", productCount, memberId);
        return new ZigzagImportResult(productCount);
    }

    private void importProduct(Long memberId, Member member, SalesChannel channel, JsonNode zigzagProduct) {
        String externalProductId = zigzagProduct.path("id").asString();
        String name = zigzagProduct.path("name").asString();
        BigDecimal price = BigDecimal.valueOf(zigzagProduct.path("price").path("original_price").asInt(0));

        Product product = productRepository
                .findByMemberIdAndExternalChannelTypeAndExternalProductId(memberId, ChannelType.ZIGZAG, externalProductId)
                .map(existing -> {
                    existing.updateInfo(name, price);
                    return existing;
                })
                .orElseGet(() -> productRepository.save(
                        new Product(member, name, price, ChannelType.ZIGZAG, externalProductId)));

        for (JsonNode zigzagItem : zigzagProduct.path("item_list")) {
            importItem(memberId, channel, product, zigzagItem);
        }
    }

    private void importItem(Long memberId, SalesChannel channel, Product product, JsonNode zigzagItem) {
        String externalItemId = zigzagItem.path("id").asString();
        int quantity = zigzagItem.path("inventory").path("quantity").asInt(0);

        Optional<ProductVariant> existingVariant =
                productVariantRepository.findByProductIdAndExternalItemId(product.getId(), externalItemId);
        if (existingVariant.isPresent()) {
            reconcileStock(memberId, channel, existingVariant.get(), quantity);
            return;
        }

        Set<OptionValue> optionValues = new LinkedHashSet<>();
        for (JsonNode attribute : zigzagItem.path("attribute_list")) {
            OptionGroup group = findOrCreateOptionGroup(product, attribute.path("name").asString());
            optionValues.add(findOrCreateOptionValue(group, attribute.path("value").asString()));
        }

        ProductVariant variant = productVariantRepository.save(new ProductVariant(product, optionValues, externalItemId));
        if (quantity > 0) {
            movementService.record(memberId, variant.getId(), channel.getId(), MovementType.INBOUND, quantity, NEW_VARIANT_MEMO);
        }
    }

    private void reconcileStock(Long memberId, SalesChannel channel, ProductVariant variant, int targetQuantity) {
        int delta = targetQuantity - variant.getStockQuantity();
        if (delta == 0) {
            return;
        }
        MovementType type = delta > 0 ? MovementType.ADJUSTMENT_IN : MovementType.ADJUSTMENT_OUT;
        movementService.record(memberId, variant.getId(), channel.getId(), type, Math.abs(delta), SYNC_MEMO);
    }

    private OptionGroup findOrCreateOptionGroup(Product product, String name) {
        return optionGroupRepository.findByProductIdAndName(product.getId(), name)
                .orElseGet(() -> optionGroupRepository.save(new OptionGroup(product, name)));
    }

    private OptionValue findOrCreateOptionValue(OptionGroup group, String value) {
        return optionValueRepository.findByOptionGroupIdAndValue(group.getId(), value)
                .orElseGet(() -> optionValueRepository.save(new OptionValue(group, value)));
    }
}
