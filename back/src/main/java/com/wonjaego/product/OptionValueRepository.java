package com.wonjaego.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionValueRepository extends JpaRepository<OptionValue, Long> {

    void deleteAllByOptionGroup_ProductId(Long productId);
}
