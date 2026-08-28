package com.wonjaego.channel;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelCredentialService {

    private final ChannelCredentialRepository channelCredentialRepository;
    private final MemberRepository memberRepository;
    private final CredentialEncryptor credentialEncryptor;

    // Plaintext accessKey/secretKey are encrypted here and never persisted otherwise —
    // callers must not encrypt themselves or pass already-encrypted values in. A reconnect
    // (existing row, previously disconnected or not) keeps its role/import history —
    // only a brand-new row defaults role to ORDER_SYNC.
    @Transactional
    public ChannelCredential connect(Long memberId, ChannelType channelType, String accessKey, String secretKey) {
        String encryptedAccessKey = credentialEncryptor.encrypt(accessKey);
        String encryptedSecretKey = credentialEncryptor.encrypt(secretKey);
        LocalDateTime now = LocalDateTime.now();
        return channelCredentialRepository.findByMemberIdAndChannelType(memberId, channelType)
                .map(existing -> {
                    existing.connect(encryptedAccessKey, encryptedSecretKey, now);
                    return existing;
                })
                .orElseGet(() -> {
                    Member member = memberRepository.getReferenceById(memberId);
                    return channelCredentialRepository.save(
                            new ChannelCredential(member, channelType, encryptedAccessKey, encryptedSecretKey, now));
                });
    }

    // Ownership must already be verified by the caller (see ChannelService) — this only
    // clears keys and flips status, it does not look the row up by memberId itself.
    @Transactional
    public void disconnect(ChannelCredential credential) {
        credential.disconnect();
    }

    // At most one CONNECTED channel per member may hold PRODUCT_SOURCE — checked against
    // every other credential row for this member, not just connected ones' roles, since a
    // disconnected row's stale role shouldn't block a different channel from taking it.
    @Transactional
    public void changeRole(ChannelCredential credential, ChannelRole newRole) {
        if (newRole == ChannelRole.PRODUCT_SOURCE
                && channelCredentialRepository.existsByMemberIdAndRoleAndStatusAndIdNot(
                        credential.getMember().getId(), ChannelRole.PRODUCT_SOURCE, ChannelConnectionStatus.CONNECTED, credential.getId())) {
            throw new InvalidChannelOperationException("상품 소스는 1개 채널만 지정할 수 있어요.");
        }
        credential.changeRole(newRole);
    }

    @Transactional
    public void recordImport(Long memberId, ChannelType channelType, int count) {
        channelCredentialRepository.findByMemberIdAndChannelType(memberId, channelType)
                .ifPresent(credential -> credential.recordImport(LocalDateTime.now(), count));
    }

    @Transactional(readOnly = true)
    public DecryptedCredential getDecrypted(Long memberId, ChannelType channelType) {
        ChannelCredential credential = channelCredentialRepository.findByMemberIdAndChannelType(memberId, channelType)
                .orElseThrow(() -> new ChannelCredentialNotFoundException(channelType));
        return new DecryptedCredential(
                credentialEncryptor.decrypt(credential.getEncryptedAccessKey()),
                credentialEncryptor.decrypt(credential.getEncryptedSecretKey()));
    }
}
