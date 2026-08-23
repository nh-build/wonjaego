package com.wonjaego.channel;

import com.wonjaego.common.BaseEntity;
import com.wonjaego.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Access/secret key are always stored encrypted (via CredentialEncryptor) — this entity never
// holds plaintext, in memory or otherwise, past the moment ChannelCredentialService receives it.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "channel_credentials", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "channel_type"}))
public class ChannelCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType channelType;

    @Column(nullable = false)
    private String encryptedAccessKey;

    @Column(nullable = false)
    private String encryptedSecretKey;

    public ChannelCredential(Member member, ChannelType channelType, String encryptedAccessKey, String encryptedSecretKey) {
        this.member = member;
        this.channelType = channelType;
        this.encryptedAccessKey = encryptedAccessKey;
        this.encryptedSecretKey = encryptedSecretKey;
    }

    public void updateKeys(String encryptedAccessKey, String encryptedSecretKey) {
        this.encryptedAccessKey = encryptedAccessKey;
        this.encryptedSecretKey = encryptedSecretKey;
    }
}
