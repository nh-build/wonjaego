package com.wonjaego.movement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    @Query("SELECT m FROM Movement m JOIN FETCH m.salesChannel WHERE m.product.id = :productId ORDER BY m.createdAt DESC, m.id DESC")
    List<Movement> findAllByProductIdWithChannel(@Param("productId") Long productId);

    boolean existsByProductId(Long productId);

    boolean existsBySalesChannelId(Long salesChannelId);
}
