package com.wonjaego.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {

    void deleteAllByProductId(Long productId);
}
