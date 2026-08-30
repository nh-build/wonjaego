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

    @Transactional(readOnly = true)
    public int getLowStockThreshold(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow().getLowStockThreshold();
    }

    @Transactional
    public void updateLowStockThreshold(Long memberId, int lowStockThreshold) {
        if (lowStockThreshold < 0) {
            throw new InvalidLowStockThresholdException("재고 임박 기준은 0 이상이어야 합니다.");
        }
        memberRepository.getReferenceById(memberId).updateLowStockThreshold(lowStockThreshold);
    }
}
