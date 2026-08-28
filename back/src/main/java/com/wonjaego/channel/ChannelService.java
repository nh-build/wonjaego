package com.wonjaego.channel;

import com.wonjaego.integration.zigzag.ZigzagImportResult;
import com.wonjaego.integration.zigzag.ZigzagProductImportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelService {

    // Display order for the /channels screens — deliberately not ChannelType's declared
    // order, to match the mockups (지그재그, 쿠팡, 스마트스토어, 에이블리) without reordering
    // the enum itself and risking unrelated call sites that iterate ChannelType.values().
    private static final List<ChannelType> DISPLAY_ORDER =
            List.of(ChannelType.ZIGZAG, ChannelType.COUPANG, ChannelType.SMARTSTORE, ChannelType.ABLY);

    private final ChannelCredentialRepository channelCredentialRepository;
    private final ChannelCredentialService channelCredentialService;
    private final ZigzagProductImportService zigzagProductImportService;

    @Transactional(readOnly = true)
    public List<ChannelListItem> listChannels(Long memberId) {
        return DISPLAY_ORDER.stream()
                .map(type -> toListItem(memberId, type))
                .toList();
    }

    private ChannelListItem toListItem(Long memberId, ChannelType type) {
        return channelCredentialRepository.findByMemberIdAndChannelType(memberId, type)
                .filter(credential -> credential.getStatus() == ChannelConnectionStatus.CONNECTED)
                .map(credential -> new ChannelListItem(type, true, credential.getId()))
                .orElseGet(() -> new ChannelListItem(type, false, null));
    }

    @Transactional(readOnly = true)
    public ChannelManageView getManageView(Long memberId, Long credentialId) {
        ChannelCredential credential = getOwned(memberId, credentialId);
        boolean canImportProducts = credential.getChannelType() == ChannelType.ZIGZAG
                && credential.getRole() == ChannelRole.PRODUCT_SOURCE;
        return new ChannelManageView(
                credential.getId(),
                credential.getChannelType(),
                credential.getStatus(),
                credential.getRole(),
                credential.getConnectedAt(),
                canImportProducts,
                credential.getLastImportedAt(),
                credential.getLastImportedCount());
    }

    @Transactional
    public void changeRole(Long memberId, Long credentialId, ChannelRole newRole) {
        ChannelCredential credential = getOwned(memberId, credentialId);
        channelCredentialService.changeRole(credential, newRole);
    }

    @Transactional
    public void disconnect(Long memberId, Long credentialId) {
        ChannelCredential credential = getOwned(memberId, credentialId);
        channelCredentialService.disconnect(credential);
    }

    @Transactional
    public ZigzagImportResult importProducts(Long memberId, Long credentialId) {
        ChannelCredential credential = getOwned(memberId, credentialId);
        if (credential.getChannelType() != ChannelType.ZIGZAG || credential.getRole() != ChannelRole.PRODUCT_SOURCE) {
            throw new InvalidChannelOperationException("상품 소스로 지정된 지그재그 채널만 상품을 가져올 수 있어요.");
        }
        return zigzagProductImportService.importProducts(memberId);
    }

    private ChannelCredential getOwned(Long memberId, Long credentialId) {
        return channelCredentialRepository.findByIdAndMemberId(credentialId, memberId)
                .orElseThrow(() -> new ChannelCredentialNotFoundException(credentialId));
    }
}
