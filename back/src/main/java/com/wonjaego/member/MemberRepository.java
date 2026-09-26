package com.wonjaego.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);

    // Not Optional — email has no unique constraint (it's an optional signup field), so more
    // than one account can share the same address.
    List<Member> findByEmail(String email);
}
