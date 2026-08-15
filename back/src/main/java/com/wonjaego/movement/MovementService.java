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
        };

        product.adjustStock(quantityChange);
        Movement movement = new Movement(product, channel, type, quantityChange, memo);
        return movementRepository.save(movement);
    }
}
