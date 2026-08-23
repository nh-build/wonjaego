package com.wonjaego.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ChannelCredentialServiceTest {

    @Autowired
    private ChannelCredentialService channelCredentialService;

    @Autowired
    private ChannelCredentialRepository channelCredentialRepository;

    @Autowired
    private MemberService memberService;

    private Long createMember(String username) {
        Member member = memberService.signUp(username, "password123", "가게-" + username);
        return member.getId();
    }

    @Test
    void 저장한_키는_복호화하면_원래_값으로_돌아온다() {
        Long memberId = createMember("cred1");

        channelCredentialService.saveOrUpdate(memberId, ChannelType.ZIGZAG, "my-access-key", "my-secret-key");

        DecryptedCredential decrypted = channelCredentialService.getDecrypted(memberId, ChannelType.ZIGZAG);
        assertThat(decrypted.accessKey()).isEqualTo("my-access-key");
        assertThat(decrypted.secretKey()).isEqualTo("my-secret-key");
    }

    @Test
    void DB에는_평문이_아니라_암호화된_값만_저장된다() {
        Long memberId = createMember("cred2");

        channelCredentialService.saveOrUpdate(memberId, ChannelType.ZIGZAG, "plain-access", "plain-secret");

        ChannelCredential stored = channelCredentialRepository.findByMemberIdAndChannelType(memberId, ChannelType.ZIGZAG)
                .orElseThrow();
        assertThat(stored.getEncryptedAccessKey()).isNotEqualTo("plain-access");
        assertThat(stored.getEncryptedSecretKey()).isNotEqualTo("plain-secret");
        assertThat(stored.getEncryptedAccessKey()).doesNotContain("plain-access");
        assertThat(stored.getEncryptedSecretKey()).doesNotContain("plain-secret");
    }

    @Test
    void 같은_채널에_다시_저장하면_기존_행을_갱신한다() {
        Long memberId = createMember("cred3");

        channelCredentialService.saveOrUpdate(memberId, ChannelType.ZIGZAG, "old-access", "old-secret");
        channelCredentialService.saveOrUpdate(memberId, ChannelType.ZIGZAG, "new-access", "new-secret");

        assertThat(channelCredentialRepository.count()).isEqualTo(1);
        DecryptedCredential decrypted = channelCredentialService.getDecrypted(memberId, ChannelType.ZIGZAG);
        assertThat(decrypted.accessKey()).isEqualTo("new-access");
        assertThat(decrypted.secretKey()).isEqualTo("new-secret");
    }

    @Test
    void 연동하지_않은_채널을_조회하면_404_예외가_발생한다() {
        Long memberId = createMember("cred4");

        assertThrows(ChannelCredentialNotFoundException.class,
                () -> channelCredentialService.getDecrypted(memberId, ChannelType.ZIGZAG));
    }
}
