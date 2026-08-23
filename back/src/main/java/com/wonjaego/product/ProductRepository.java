package com.wonjaego.product;

import com.wonjaego.channel.ChannelType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByMemberId(Long memberId);

    Optional<Product> findByIdAndMemberId(Long id, Long memberId);

    // Limit applied in the query itself (not after fetching) — a broad query can't load
    // the seller's whole product table into memory just to truncate it.
    List<Product> findByMemberIdAndNameContainingIgnoreCase(Long memberId, String name, Limit limit);

    // Backs 채널 연동 import's find-or-create dedup by the source channel's own product id.
    Optional<Product> findByMemberIdAndExternalChannelTypeAndExternalProductId(
            Long memberId, ChannelType externalChannelType, String externalProductId);
}
