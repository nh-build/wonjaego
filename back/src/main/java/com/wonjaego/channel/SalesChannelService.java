package com.wonjaego.channel;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.movement.MovementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesChannelService {

    private final SalesChannelRepository salesChannelRepository;
    private final MemberRepository memberRepository;
    private final MovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<SalesChannel> listOwned(Long memberId) {
        return salesChannelRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public SalesChannel getOwned(Long memberId, Long channelId) {
        return salesChannelRepository.findByIdAndMemberId(channelId, memberId)
                .orElseThrow(() -> new SalesChannelNotFoundException(channelId));
    }

    @Transactional
    public SalesChannel create(Long memberId, SalesChannelForm form) {
        if (salesChannelRepository.existsByMemberIdAndName(memberId, form.getName())) {
            throw new DuplicateChannelNameException(form.getName());
        }
        Member member = memberRepository.getReferenceById(memberId);
        SalesChannel channel = new SalesChannel(member, form.getName());
        return salesChannelRepository.save(channel);
    }

    @Transactional
    public SalesChannel update(Long memberId, Long channelId, SalesChannelForm form) {
        SalesChannel channel = getOwned(memberId, channelId);
        if (salesChannelRepository.existsByMemberIdAndNameAndIdNot(memberId, form.getName(), channelId)) {
            throw new DuplicateChannelNameException(form.getName());
        }
        channel.rename(form.getName());
        return channel;
    }

    @Transactional
    public void delete(Long memberId, Long channelId) {
        SalesChannel channel = getOwned(memberId, channelId);
        if (movementRepository.existsBySalesChannelId(channelId)) {
            throw new SalesChannelHasMovementsException(channel.getName());
        }
        salesChannelRepository.delete(channel);
    }
}
