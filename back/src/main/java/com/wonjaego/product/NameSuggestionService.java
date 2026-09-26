package com.wonjaego.product;

import com.wonjaego.ai.NameSuggestionClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Thin validation layer in front of NameSuggestionClient — keeps ProductController from having
// to know about photo/category validation rules, mirroring ProductService.validatePhoto()'s
// content-type/size checks (a smaller cap here since this photo is never persisted, only
// forwarded to Gemini).
@Service
@RequiredArgsConstructor
public class NameSuggestionService {

    private static final Set<String> ALLOWED_PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_SIZE_BYTES = 3L * 1024 * 1024;

    private final NameSuggestionClient nameSuggestionClient;

    public List<String> suggest(MultipartFile photo, String category) {
        if (photo == null || photo.isEmpty()) {
            throw new InvalidNameSuggestionRequestException("상품 사진을 먼저 선택해주세요.");
        }
        String contentType = photo.getContentType();
        if (contentType == null || !ALLOWED_PHOTO_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidNameSuggestionRequestException("JPEG, PNG, WebP 형식만 지원합니다.");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new InvalidNameSuggestionRequestException("사진 파일이 너무 큽니다.");
        }

        byte[] photoBytes;
        try {
            photoBytes = photo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return nameSuggestionClient.suggest(photoBytes, contentType, category);
    }
}
