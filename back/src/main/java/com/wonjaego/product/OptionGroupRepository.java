package com.wonjaego.product;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {

    // Backs 채널 연동 re-import — reuse an existing group (e.g. "색상") instead of creating
    // a duplicate every time the same product is re-synced.
    Optional<OptionGroup> findByProductIdAndName(Long productId, String name);

    void deleteAllByProductId(Long productId);
}
