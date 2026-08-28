package com.wonjaego.channel;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelCredentialRepository extends JpaRepository<ChannelCredential, Long> {

    Optional<ChannelCredential> findByMemberIdAndChannelType(Long memberId, ChannelType channelType);

    Optional<ChannelCredential> findByIdAndMemberId(Long id, Long memberId);

    boolean existsByMemberIdAndRoleAndStatusAndIdNot(Long memberId, ChannelRole role, ChannelConnectionStatus status, Long id);
}
