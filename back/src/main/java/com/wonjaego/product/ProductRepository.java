package com.wonjaego.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByMemberId(Long memberId);

    Optional<Product> findByIdAndMemberId(Long id, Long memberId);

    boolean existsByMemberIdAndSku(Long memberId, String sku);

    boolean existsByMemberIdAndSkuAndIdNot(Long memberId, String sku, Long id);
}
