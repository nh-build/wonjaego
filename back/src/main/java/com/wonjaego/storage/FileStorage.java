package com.wonjaego.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// A key-addressed file store. Product photos are the only current caller (see
// docs/adr/0003-product-photo-storage-abstraction.md) — callers hold only the opaque key
// this returns, never a filesystem path, so swapping the implementation (e.g. to a cloud
// object store) never requires touching a caller.
public interface FileStorage {

    String store(MultipartFile file) throws IOException;

    Resource load(String key);

    void delete(String key);
}
