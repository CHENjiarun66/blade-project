package com.blade.file.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile store(MultipartFile file, String businessType);

    Resource load(String storagePath);

    void delete(String storagePath);

    String getStorageType();
}
