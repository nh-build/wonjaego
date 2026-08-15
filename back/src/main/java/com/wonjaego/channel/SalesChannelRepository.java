package com.wonjaego.channel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesChannelRepository extends JpaRepository<SalesChannel, Long> {

    List<SalesChannel> findAllByMemberId(Long memberId);

    Optional<SalesChannel> findByIdAndMemberId(Long id, Long memberId);

    boolean existsByMemberIdAndName(Long memberId, String name);

    boolean existsByMemberIdAndNameAndIdNot(Long memberId, String name, Long id);
}
