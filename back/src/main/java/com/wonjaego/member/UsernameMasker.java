package com.wonjaego.member;

// Shared by 아이디 찾기 (shows the masked username directly) and 비밀번호 재설정 (labels which
// account each emailed link belongs to, when one email address matches more than one account).
final class UsernameMasker {

    private UsernameMasker() {
    }

    static String mask(String username) {
        if (username.length() <= 2) {
            return username.charAt(0) + "*".repeat(Math.max(1, username.length() - 1));
        }
        return username.substring(0, 2) + "*".repeat(username.length() - 2);
    }
}
