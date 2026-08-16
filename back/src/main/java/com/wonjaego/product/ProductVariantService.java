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

    @Transactional
    public ProductVariant update(Long memberId, Long variantId, ProductVariantEditForm form) {
        ProductVariant variant = getOwned(memberId, variantId);
        String sku = normalizeSku(form.getSku());
        if (sku != null && productVariantRepository.existsByMemberIdAndSkuAndIdNot(memberId, sku, variantId)) {
            throw new DuplicateSkuException(sku);
        }
        variant.updateSkuAndThreshold(sku, form.getLowStockThreshold());
        return variant;
    }

    // A blank SKU input means "no SKU" — store null, not an empty string, so it stays
    // consistent with a freshly-generated variant's initial state.
    private String normalizeSku(String sku) {
        if (sku == null) {
            return null;
        }
        String trimmed = sku.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
