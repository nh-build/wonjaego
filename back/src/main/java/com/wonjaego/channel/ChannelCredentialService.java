package com.wonjaego.channel;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
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
    // callers must not encrypt themselves or pass already-encrypted values in.
    @Transactional
    public void saveOrUpdate(Long memberId, ChannelType channelType, String accessKey, String secretKey) {
        String encryptedAccessKey = credentialEncryptor.encrypt(accessKey);
        String encryptedSecretKey = credentialEncryptor.encrypt(secretKey);
        channelCredentialRepository.findByMemberIdAndChannelType(memberId, channelType)
                .ifPresentOrElse(
                        existing -> existing.updateKeys(encryptedAccessKey, encryptedSecretKey),
                        () -> {
                            Member member = memberRepository.getReferenceById(memberId);
                            channelCredentialRepository.save(
                                    new ChannelCredential(member, channelType, encryptedAccessKey, encryptedSecretKey));
                        });
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
