package com.wonjaego.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// Swallows send failures (logs only) rather than propagating them — callers (password reset)
// must return the same "메일을 보냈습니다" response whether or not the send actually succeeded,
// so a caller-visible exception here would defeat that account-enumeration protection.
@Slf4j
@Component
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("이메일 발송 실패 (to={}, subject={})", to, subject, e);
        }
    }
}
