package com.wonjaego.integration.zigzag;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Builds the 지그재그(카카오스타일) Open API Authorization header, following the official
// Java example exactly: the header text says "algorithm=HmacSHA256" but the signature is
// actually computed with HmacSHA1 — that mismatch is how Zigzag's API works, not a bug here.
public final class ZigzagAuthorizationHeaderFactory {

    private static final String MAC_ALGORITHM = "HmacSHA1";

    private ZigzagAuthorizationHeaderFactory() {
    }

    public static String create(String accessKey, String secretKey, String query) {
        return create(accessKey, secretKey, query, System.currentTimeMillis());
    }

    // Package-private overload with an explicit signedDate so the signature is
    // deterministic and testable without stubbing the clock.
    static String create(String accessKey, String secretKey, String query, long signedDate) {
        String normalizedQuery = query.replaceAll("\\s+", " ");
        String message = signedDate + "." + normalizedQuery;
        String signature = sign(message, secretKey);
        return "CEA algorithm=HmacSHA256, access-key=" + accessKey
                + ", signed-date=" + signedDate + ", signature=" + signature;
    }

    private static String sign(String message, String secretKey) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), MAC_ALGORITHM));
            return toHexString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("지그재그 Authorization 서명 생성에 실패했습니다.", e);
        }
    }

    private static String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}
