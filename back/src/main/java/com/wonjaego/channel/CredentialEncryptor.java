package com.wonjaego.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

// Encrypts channel API keys before they reach the database (never store plaintext).
// The salt doesn't need to be secret (only the password does) — it just needs to stay
// constant, since changing it would make every previously-encrypted value undecryptable.
@Slf4j
@Component
public class CredentialEncryptor {

    private static final String SALT = "888cbe33d5f7113b";

    private final TextEncryptor textEncryptor;

    public CredentialEncryptor(@Value("${wonjaego.encryption.key}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            // Not a fail-fast — dev/test must still boot without it, same as
            // ANTHROPIC_API_KEY's empty-default precedent — but a blank key materially
            // weakens the derived AES key, so any real deploy needs this visible in logs.
            log.warn("WONJAEGO_ENCRYPTION_KEY이 설정되지 않았습니다 — 채널 API 키 암호화가 약한 기본값으로 동작합니다. "
                    + "실제 배포 환경에서는 반드시 설정하세요.");
        }
        this.textEncryptor = Encryptors.delux(encryptionKey, SALT);
    }

    public String encrypt(String plainText) {
        return textEncryptor.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        return textEncryptor.decrypt(cipherText);
    }
}
