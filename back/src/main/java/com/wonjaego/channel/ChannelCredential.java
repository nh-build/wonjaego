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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Access/secret key are always stored encrypted (via CredentialEncryptor) — this entity never
// holds plaintext, in memory or otherwise, past the moment ChannelCredentialService receives it.
// A row survives disconnect (status flips to NOT_CONNECTED, keys are cleared) so a seller's
// role choice and import history aren't lost on reconnect.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelConnectionStatus status;

    private String encryptedAccessKey;

    private String encryptedSecretKey;

    private LocalDateTime connectedAt;

    private LocalDateTime lastImportedAt;

    private Integer lastImportedCount;

    public ChannelCredential(Member member, ChannelType channelType, String encryptedAccessKey, String encryptedSecretKey,
                              LocalDateTime connectedAt) {
        this.member = member;
        this.channelType = channelType;
        this.role = ChannelRole.ORDER_SYNC;
        this.status = ChannelConnectionStatus.CONNECTED;
        this.encryptedAccessKey = encryptedAccessKey;
        this.encryptedSecretKey = encryptedSecretKey;
        this.connectedAt = connectedAt;
    }

    public void connect(String encryptedAccessKey, String encryptedSecretKey, LocalDateTime connectedAt) {
        this.encryptedAccessKey = encryptedAccessKey;
        this.encryptedSecretKey = encryptedSecretKey;
        this.status = ChannelConnectionStatus.CONNECTED;
        this.connectedAt = connectedAt;
    }

    public void disconnect() {
        this.encryptedAccessKey = null;
        this.encryptedSecretKey = null;
        this.status = ChannelConnectionStatus.NOT_CONNECTED;
    }

    public void changeRole(ChannelRole role) {
        this.role = role;
    }

    public void recordImport(LocalDateTime importedAt, int count) {
        this.lastImportedAt = importedAt;
        this.lastImportedCount = count;
    }
}
