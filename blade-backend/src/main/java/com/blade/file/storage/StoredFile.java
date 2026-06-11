package com.blade.file.storage;

public class StoredFile {

    private final String fileKey;
    private final String fileName;
    private final String storagePath;
    private final String storageType;

    public StoredFile(String fileKey, String fileName, String storagePath, String storageType) {
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.storageType = storageType;
    }

    public String getFileKey() { return fileKey; }
    public String getFileName() { return fileName; }
    public String getStoragePath() { return storagePath; }
    public String getStorageType() { return storageType; }
}
