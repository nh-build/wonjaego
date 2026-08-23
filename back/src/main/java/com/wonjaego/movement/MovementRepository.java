package com.wonjaego.movement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    // LEFT JOIN FETCH — salesChannel is nullable (e.g. registration-time initial stock),
    // an inner JOIN FETCH would silently drop channel-less movements from these results.
    @Query("SELECT m FROM Movement m LEFT JOIN FETCH m.salesChannel "
            + "WHERE m.variant.id = :variantId ORDER BY m.createdAt DESC, m.id DESC")
    List<Movement> findAllByVariantIdWithChannel(@Param("variantId") Long variantId);

    @Query("SELECT m FROM Movement m LEFT JOIN FETCH m.salesChannel JOIN FETCH m.variant v "
            + "LEFT JOIN FETCH v.optionValues ov LEFT JOIN FETCH ov.optionGroup "
            + "WHERE m.variant.product.id = :productId ORDER BY m.createdAt DESC, m.id DESC")
    List<Movement> findAllByProductIdWithChannelAndVariant(@Param("productId") Long productId);

    boolean existsByVariant_ProductId(Long productId);

    boolean existsBySalesChannelId(Long salesChannelId);
}
