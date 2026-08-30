package com.wonjaego.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {

    // Backs 채널 연동 re-import — reuse an existing group (e.g. "색상") instead of creating
    // a duplicate every time the same product is re-synced.
    Optional<OptionGroup> findByProductIdAndName(Long productId, String name);

    // Backs the product-edit screen's group-identity resolution (existing group id ->
    // reuse/rename vs. a submitted id with no match -> reject as cross-tenant/stale).
    List<OptionGroup> findAllByProductIdOrderById(Long productId);

    void deleteAllByProductId(Long productId);
}
