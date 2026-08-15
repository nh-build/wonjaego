package com.wonjaego.product;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.movement.MovementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final MovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<Product> listOwned(Long memberId) {
        return productRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public Product getOwned(Long memberId, Long productId) {
        return productRepository.findByIdAndMemberId(productId, memberId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public Product create(Long memberId, ProductCreateForm form) {
        if (productRepository.existsByMemberIdAndSku(memberId, form.getSku())) {
            throw new DuplicateSkuException(form.getSku());
        }
        Member member = memberRepository.getReferenceById(memberId);
        Product product = new Product(member, form.getName(), form.getSku(), form.getPrice(),
                form.getStockQuantity(), form.getLowStockThreshold());
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long memberId, Long productId, ProductEditForm form) {
        Product product = getOwned(memberId, productId);
        if (productRepository.existsByMemberIdAndSkuAndIdNot(memberId, form.getSku(), productId)) {
            throw new DuplicateSkuException(form.getSku());
        }
        product.updateInfo(form.getName(), form.getSku(), form.getPrice(), form.getLowStockThreshold());
        return product;
    }

    @Transactional
    public void delete(Long memberId, Long productId) {
        Product product = getOwned(memberId, productId);
        if (movementRepository.existsByProductId(productId)) {
            throw new ProductHasMovementsException(product.getName());
        }
        productRepository.delete(product);
    }
}
