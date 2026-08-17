package com.wonjaego.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path baseDirectory;

    public LocalFileStorage(@Value("${wonjaego.storage.local.base-directory}") String baseDirectory) {
        this.baseDirectory = Path.of(baseDirectory);
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        Files.createDirectories(baseDirectory);
        String key = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        file.transferTo(baseDirectory.resolve(key));
        return key;
    }

    @Override
    public Resource load(String key) {
        return new FileSystemResource(baseDirectory.resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(baseDirectory.resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The caller's original filename is untrusted input, not just a display label — the
    // extension extracted here becomes part of a path passed to baseDirectory.resolve(),
    // so anything containing '/' or extra '.' characters is rejected rather than trusted.
    // A crafted name like "evil.jpg/../../../etc/passwd" would otherwise leak '/' into the
    // generated key and make resolve() treat it as extra path segments.
    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        String extension = originalFilename.substring(dotIndex);
        return extension.matches("\\.[A-Za-z0-9]{1,10}") ? extension : "";
    }
}
