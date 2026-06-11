package com.blade.file.storage;

import com.blade.file.config.FileStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileStorageProperties properties;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredFile store(MultipartFile file, String businessType) {
        try {
            String safeBusinessType = sanitizePathPart(businessType);
            String datePath = LocalDate.now().format(DATE_PATH);
            String extension = getExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;
            String fileKey = safeBusinessType + "/" + datePath + "/" + fileName;
            Path target = basePath().resolve(fileKey).normalize();
            if (!target.startsWith(basePath())) {
                throw new RuntimeException("非法文件路径");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new StoredFile(fileKey, fileName, target.toString(), getStorageType());
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }
    }

    @Override
    public Resource load(String storagePath) {
        Path path = Path.of(storagePath).normalize();
        if (!path.startsWith(basePath())) {
            throw new RuntimeException("非法文件路径");
        }
        Resource resource = new FileSystemResource(path);
        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("文件不存在");
        }
        return resource;
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path path = Path.of(storagePath).normalize();
            if (path.startsWith(basePath())) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("文件删除失败", e);
        }
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    private Path basePath() {
        return Path.of(properties.getLocalBasePath()).toAbsolutePath().normalize();
    }

    private String getExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int index = originalName.lastIndexOf('.');
        if (index < 0 || index == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(index).toLowerCase(Locale.ROOT);
    }

    private String sanitizePathPart(String value) {
        if (value == null || value.isBlank()) {
            return "common";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
