package com.wonjaego.movement;

import com.wonjaego.channel.SalesChannel;
import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.product.Product;
import com.wonjaego.product.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovementService {

    private final MovementRepository movementRepository;
    private final ProductService productService;
    private final SalesChannelService salesChannelService;

    @Transactional(readOnly = true)
    public List<Movement> listForProduct(Long memberId, Long productId) {
        productService.getOwned(memberId, productId);
        return movementRepository.findAllByProductIdWithChannel(productId);
    }

    @Transactional
    public Movement record(Long memberId, Long productId, Long salesChannelId, MovementType type, int quantity, String memo) {
        Product product = productService.getOwned(memberId, productId);
        SalesChannel channel = salesChannelService.getOwned(memberId, salesChannelId);

        int quantityChange = switch (type) {
            case INBOUND, RETURN -> quantity;
            case SALE -> -quantity;
            case EXCHANGE -> throw new IllegalArgumentException("EXCHANGE는 recordExchange()로 기록해야 합니다.");
        };

        product.adjustStock(quantityChange);
        Movement movement = new Movement(product, channel, type, quantityChange, memo);
        return movementRepository.save(movement);
    }

    @Transactional
    public void recordExchange(Long memberId, Long originalProductId, Long salesChannelId, Long newProductId,
                                int quantity, String memo) {
        Product originalProduct = productService.getOwned(memberId, originalProductId);
        SalesChannel channel = salesChannelService.getOwned(memberId, salesChannelId);

        if (newProductId == null || newProductId.equals(originalProductId)) {
            movementRepository.save(new Movement(originalProduct, channel, MovementType.EXCHANGE, 0, memo));
            return;
        }

        Product newProduct = productService.getOwned(memberId, newProductId);

        // newProduct's adjustment is the only one that can fail (it's the only negative
        // delta), so it must run first — otherwise a shortage on newProduct would leave
        // originalProduct already mutated in-memory before the exception unwinds.
        newProduct.adjustStock(-quantity);
        originalProduct.adjustStock(quantity);

        movementRepository.save(new Movement(originalProduct, channel, MovementType.EXCHANGE, quantity, memo));
        movementRepository.save(new Movement(newProduct, channel, MovementType.EXCHANGE, -quantity, memo));
    }
}
