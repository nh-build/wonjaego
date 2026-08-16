package com.wonjaego.product;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<ProductVariant> listForProduct(Long memberId, Long productId) {
        productService.getOwned(memberId, productId);
        return productVariantRepository.findAllByProductIdWithOptions(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductVariant> listOwned(Long memberId) {
        return productVariantRepository.findAllByMemberIdWithProductAndOptions(memberId);
    }

    @Transactional(readOnly = true)
    public ProductVariant getOwned(Long memberId, Long variantId) {
        return productVariantRepository.findByIdAndMemberId(variantId, memberId)
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));
    }
}
