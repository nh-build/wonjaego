package com.wonjaego.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_VALID_MINUTES = 30;

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String appBaseUrl;

    public PasswordResetService(MemberRepository memberRepository,
                                 PasswordResetTokenRepository tokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 MailService mailService,
                                 @Value("${wonjaego.app.base-url}") String appBaseUrl) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.appBaseUrl = appBaseUrl;
    }

    // Caller must show the same "메일을 보냈습니다" response regardless of whether an account
    // matched (account-enumeration protection) — so this method never reports back whether it
    // actually sent anything; it just does it quietly when there's a match.
    @Transactional
    public void requestReset(String email) {
        List<Member> members = memberRepository.findByEmail(email);
        if (members.isEmpty()) {
            return;
        }

        StringBuilder body = new StringBuilder(
                "아래 링크를 눌러 비밀번호를 재설정하세요. 링크는 발급 후 30분 동안만 유효합니다.\n\n");
        for (Member member : members) {
            String rawToken = generateRawToken();
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_VALID_MINUTES);
            tokenRepository.save(new PasswordResetToken(member, hash(rawToken), expiresAt));
            body.append("계정 ").append(UsernameMasker.mask(member.getUsername())).append(": ")
                    .append(appBaseUrl).append("/reset-password/confirm?token=").append(rawToken)
                    .append("\n\n");
        }
        mailService.send(email, "[원재고] 비밀번호 재설정 안내", body.toString());
    }

    @Transactional(readOnly = true)
    public boolean isTokenUsable(String rawToken) {
        return tokenRepository.findByTokenHash(hash(rawToken))
                .map(token -> token.isUsable(LocalDateTime.now()))
                .orElse(false);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .filter(candidate -> candidate.isUsable(LocalDateTime.now()))
                .orElseThrow(() -> new InvalidOrExpiredTokenException("재설정 링크가 만료되었거나 이미 사용됐습니다."));
        token.getMember().updatePassword(passwordEncoder.encode(newPassword));
        token.markUsed(LocalDateTime.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
