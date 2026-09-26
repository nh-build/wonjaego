package com.wonjaego.member;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 아이디 찾기 — 이 흐름은 (비밀번호 재설정과 달리) 계정 존재 여부를 감춰야 하는 요건이
    // 없으므로, 일치하는 계정마다 마스킹된 아이디를 그대로 보여준다. 빈 리스트면 "계정 없음".
    @Transactional(readOnly = true)
    public List<String> findMaskedUsernamesByEmail(String email) {
        return memberRepository.findByEmail(email).stream()
                .map(member -> UsernameMasker.mask(member.getUsername()))
                .toList();
    }

    @Transactional
    public Member signUp(String username, String rawPassword, String businessName, String email) {
        if (memberRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        Member member = new Member(username, passwordEncoder.encode(rawPassword), businessName, email);
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

    // 프로필/더보기 화면은 principal.getMember()를 쓰지 않고 항상 이걸로 다시 조회한다 —
    // MemberPrincipal은 로그인 시점에 세션에 캐시된 Member라서, 프로필 수정 후 재로그인
    // 전까지는 바뀐 이름이 세션에 반영되지 않는다(세션 재인증을 강제하지 않는 한).
    @Transactional(readOnly = true)
    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow();
    }

    @Transactional
    public void updateBusinessName(Long memberId, String businessName) {
        memberRepository.getReferenceById(memberId).updateBusinessName(businessName);
    }

    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new InvalidCurrentPasswordException("현재 비밀번호가 올바르지 않습니다.");
        }
        member.updatePassword(passwordEncoder.encode(newPassword));
    }
}
