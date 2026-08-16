package com.wonjaego.movement;

import com.wonjaego.channel.SalesChannel;
import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.product.ProductService;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovementService {

    private final MovementRepository movementRepository;
    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final SalesChannelService salesChannelService;

    @Transactional(readOnly = true)
    public List<Movement> listForProduct(Long memberId, Long productId) {
        productService.getOwned(memberId, productId);
        return movementRepository.findAllByProductIdWithChannelAndVariant(productId);
    }

    @Transactional
    public Movement record(Long memberId, Long variantId, Long salesChannelId, MovementType type, int quantity, String memo) {
        ProductVariant variant = productVariantService.getOwned(memberId, variantId);
        SalesChannel channel = salesChannelService.getOwned(memberId, salesChannelId);

        int quantityChange = switch (type) {
            case INBOUND, RETURN -> quantity;
            case SALE -> -quantity;
            case EXCHANGE -> throw new IllegalArgumentException("EXCHANGE는 recordExchange()로 기록해야 합니다.");
        };

        variant.adjustStock(quantityChange);
        Movement movement = new Movement(variant, channel, type, quantityChange, memo);
        return movementRepository.save(movement);
    }

    @Transactional
    public void recordExchange(Long memberId, Long originalVariantId, Long salesChannelId, Long newVariantId,
                                int quantity, String memo) {
        ProductVariant originalVariant = productVariantService.getOwned(memberId, originalVariantId);
        SalesChannel channel = salesChannelService.getOwned(memberId, salesChannelId);

        if (newVariantId == null || newVariantId.equals(originalVariantId)) {
            movementRepository.save(new Movement(originalVariant, channel, MovementType.EXCHANGE, 0, memo));
            return;
        }

        ProductVariant newVariant = productVariantService.getOwned(memberId, newVariantId);

        // newVariant's adjustment is the only one that can fail (it's the only negative
        // delta), so it must run first — otherwise a shortage on newVariant would leave
        // originalVariant already mutated in-memory before the exception unwinds.
        newVariant.adjustStock(-quantity);
        originalVariant.adjustStock(quantity);

        movementRepository.save(new Movement(originalVariant, channel, MovementType.EXCHANGE, quantity, memo));
        movementRepository.save(new Movement(newVariant, channel, MovementType.EXCHANGE, -quantity, memo));
    }
}
