package com.wonjaego.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // LEFT JOIN — a variant may have zero option values (the no-option, single-variant product case).
    @Query("SELECT DISTINCT v FROM ProductVariant v "
            + "LEFT JOIN FETCH v.optionValues ov LEFT JOIN FETCH ov.optionGroup "
            + "WHERE v.product.id = :productId ORDER BY v.id")
    List<ProductVariant> findAllByProductIdWithOptions(@Param("productId") Long productId);

    @Query("SELECT DISTINCT v FROM ProductVariant v "
            + "JOIN FETCH v.product "
            + "LEFT JOIN FETCH v.optionValues ov LEFT JOIN FETCH ov.optionGroup "
            + "WHERE v.member.id = :memberId ORDER BY v.product.id, v.id")
    List<ProductVariant> findAllByMemberIdWithProductAndOptions(@Param("memberId") Long memberId);

    // Fetches product AND optionValues/optionGroup, since getOwned() backs both simple
    // ownership checks (only needs product) and detail/edit-screen rendering (needs the
    // option label too) — one query covers every current caller safely under OSIV-off.
    @Query("SELECT DISTINCT v FROM ProductVariant v "
            + "JOIN FETCH v.product "
            + "LEFT JOIN FETCH v.optionValues ov LEFT JOIN FETCH ov.optionGroup "
            + "WHERE v.id = :id AND v.member.id = :memberId")
    Optional<ProductVariant> findByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    boolean existsByMemberIdAndSkuAndIdNot(Long memberId, String sku, Long id);

    void deleteAllByProductId(Long productId);
}
