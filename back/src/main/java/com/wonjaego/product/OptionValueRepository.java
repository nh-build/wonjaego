package com.wonjaego.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionValueRepository extends JpaRepository<OptionValue, Long> {

    // Backs 채널 연동 re-import — reuse an existing value (e.g. "블랙") under a group instead
    // of creating a duplicate every time the same product is re-synced.
    Optional<OptionValue> findByOptionGroupIdAndValue(Long optionGroupId, String value);

    // Backs the product-edit screen's per-group value reuse-or-create resolution.
    List<OptionValue> findAllByOptionGroupId(Long optionGroupId);

    void deleteAllByOptionGroup_ProductId(Long productId);
}
