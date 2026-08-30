package com.wonjaego.movement;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    // Backs the 재고 탭's "최근 재고 이력" feed — same LEFT JOIN FETCH shape as
    // findAllByProductIdWithChannelAndVariant (needed under OSIV-off for optionLabel/
    // salesChannel access at render time), scoped by member instead of by product.
    // Hibernate can't push the Pageable limit into SQL alongside a collection fetch join
    // (v.optionValues) — it loads every one of the member's movements and paginates in
    // memory, same tradeoff ProductService.listPage() already accepts at this app's scale.
    @Query("SELECT m FROM Movement m LEFT JOIN FETCH m.salesChannel JOIN FETCH m.variant v JOIN FETCH v.product "
            + "LEFT JOIN FETCH v.optionValues ov LEFT JOIN FETCH ov.optionGroup "
            + "WHERE v.member.id = :memberId ORDER BY m.createdAt DESC, m.id DESC")
    List<Movement> findRecentByMemberId(@Param("memberId") Long memberId, Pageable pageable);

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

    // Per-variant version of the same guard — backs the product-edit screen's rule that a
    // variant with any movement history (stock>0 always implies at least one, per ADR 0006)
    // can't be dropped by an option-composition edit: product_variant_id is NOT NULL on
    // Movement, so deleting such a variant would violate the FK even if the edit "confirmed" it.
    boolean existsByVariant_Id(Long variantId);

    boolean existsBySalesChannelId(Long salesChannelId);
}
