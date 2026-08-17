package com.blade.file.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageService {

    StoredFile store(MultipartFile file, String businessType);

    Resource load(String storagePath);

    void delete(String storagePath);

    String getStorageType();

    /**
     * Store a derivative file from an InputStream.
     * <p>
     * The implementation is responsible for path construction, validation, and
     * returning a {@link StoredFile} with the canonical storageType and storagePath.
     * Callers must NOT concatenate provider-specific paths.
     *
     * @param originalFileKey the original file's fileKey (e.g. "common/2026/06/18/uuid.png")
     * @param variantType     derivative variant type (e.g. "thumb", "card")
     * @param inputStream     the derivative image bytes
     * @return StoredFile with provider-chosen storageType and storagePath
     */
    StoredFile storeDerivative(String originalFileKey, String variantType,
                               InputStream inputStream) throws java.io.IOException;
}
