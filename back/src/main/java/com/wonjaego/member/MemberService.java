package com.wonjaego.member;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signUp(String username, String rawPassword, String businessName) {
        if (memberRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        Member member = new Member(username, passwordEncoder.encode(rawPassword), businessName);
        return memberRepository.save(member);
    }
}
