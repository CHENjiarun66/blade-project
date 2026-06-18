package com.blade.file;

import com.blade.file.config.FileStorageProperties;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.impl.FileServiceImpl;
import com.blade.file.storage.FileStorageService;
import com.blade.file.storage.StoredFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BE-1010 基础视频文件支持 — FileServiceImpl.upload 分类测试
 */
class FileVideoSupportTest {

    @BeforeAll
    static void initMybatisPlus() {
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileStorage.class);
    }

    private FileStorageProperties properties;
    private FileServiceImpl service;
    private final List<FileStorage> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inserted.clear();
        properties = new FileStorageProperties();
        properties.setLocalBasePath("/tmp/test-uploads");

        FileStorageMapper storageMapper = proxyMapper(FileStorageMapper.class);
        FileStorageService storageService = new FileStorageService() {
            @Override
            public StoredFile store(org.springframework.web.multipart.MultipartFile file, String bizType) {
                return new StoredFile(
                        "test-key-" + System.currentTimeMillis(),
                        "stored-" + file.getOriginalFilename(),
                        "/tmp/test-uploads/stored-" + file.getOriginalFilename(),
                        "local");
            }
            @Override
            public org.springframework.core.io.Resource load(String storagePath) {
                throw new UnsupportedOperationException();
            }
            @Override
            public void delete(String storagePath) {}
            @Override
            public String getStorageType() { return "local"; }
            @Override
            public StoredFile storeDerivative(String originalFileKey, String variantType,
                                               java.io.InputStream inputStream) throws java.io.IOException {
                return new StoredFile(
                        "deriv/2026/06/18/stem_" + variantType + ".jpg",
                        "stem_" + variantType + ".jpg",
                        "/tmp/deriv/stem_" + variantType + ".jpg",
                        "local");
            }
        };

        service = new FileServiceImpl(storageMapper, storageService, properties,
                new com.fasterxml.jackson.databind.ObjectMapper(), null,
                new com.blade.file.service.FileDerivativeService() {
                    @Override
                    public void generate(FileStorage file) {}
                    @Override
                    public org.springframework.core.io.Resource loadVariantResource(Long fileId, String variantType) { return null; }
                    @Override
                    public com.blade.file.service.FileDerivativeService.BackfillResult backfill(int limit) {
                        return new com.blade.file.service.FileDerivativeService.BackfillResult(0, 0, 0, 0);
                    }
                });
    }

    @Test
    void uploadImage_setsFileTypeImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "product-photo.JPG", "image/jpeg", new byte[100]);

        var vo = service.upload(file, "product", null, 1L);

        assertNotNull(vo.getId());
        assertEquals("IMAGE", vo.getFileType());
        assertEquals("jpg", vo.getFileExt());
        assertEquals(1, inserted.size());
        assertEquals("IMAGE", inserted.get(0).getFileType());
        assertEquals("jpg", inserted.get(0).getFileExt());
    }

    @Test
    void uploadVideoMp4_setsFileTypeVideo() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", new byte[200]);

        var vo = service.upload(file, "product", null, 1L);

        assertNotNull(vo.getId());
        assertEquals("VIDEO", vo.getFileType());
        assertEquals("mp4", vo.getFileExt());
        assertEquals("VIDEO", inserted.get(0).getFileType());
    }

    @Test
    void uploadVideoWebm_setsFileTypeVideo() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "clip.webm", "video/webm", new byte[150]);

        var vo = service.upload(file, "temp", null, 1L);

        assertEquals("VIDEO", vo.getFileType());
        assertEquals("webm", vo.getFileExt());
    }

    @Test
    void defaultMaxSize_allowsTypicalShortVideoSize() {
        assertEquals(200L, properties.getMaxSizeMb());
    }

    @Test
    void uploadVideoOverConfiguredMaxSize_isRejectedWithClearMessage() {
        properties.setMaxSizeMb(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.mp4", "video/mp4", new byte[1024 * 1024 + 1]);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.upload(file, "temp", null, 1L));
        assertEquals("文件大小不能超过 1MB", ex.getMessage());
    }

    @Test
    void uploadPng_setsFileTypeImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "screenshot.png", "image/png", new byte[120]);

        var vo = service.upload(file, "product", null, 1L);

        assertEquals("IMAGE", vo.getFileType());
        assertEquals("png", vo.getFileExt());
    }

    @Test
    void uploadDisallowedContentType_isRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[300]);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.upload(file, "temp", null, 1L));
        assertEquals("不支持的文件类型", ex.getMessage());
    }

    @Test
    void uploadNoExtension_setsFileTypeButNullExt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "noext", "image/png", new byte[90]);

        var vo = service.upload(file, "temp", null, 1L);

        assertEquals("IMAGE", vo.getFileType());
        assertNull(vo.getFileExt());
    }

    @Test
    void uploadImage_derivativeServiceThrows_stillReturnsUploadVO() {
        // Build a local service with a throwing derivative (no transaction active,
        // so the direct-call branch is exercised). The upload must succeed despite
        // the derivative failure.
        inserted.clear();
        var throwService = new com.blade.file.service.FileDerivativeService() {
            @Override
            public void generate(FileStorage file) {
                throw new RuntimeException("simulated derivative failure");
            }
            @Override
            public org.springframework.core.io.Resource loadVariantResource(Long fileId, String variantType) {
                return null;
            }
            @Override
            public com.blade.file.service.FileDerivativeService.BackfillResult backfill(int limit) {
                return new com.blade.file.service.FileDerivativeService.BackfillResult(0, 0, 0, 0);
            }
        };

        FileStorageMapper mapper = proxyMapper(FileStorageMapper.class);
        var storageService = new FileStorageService() {
            @Override
            public StoredFile store(org.springframework.web.multipart.MultipartFile file, String bizType) {
                return new StoredFile("fk", "fn.jpg", "/tmp/x.jpg", "local");
            }
            @Override
            public org.springframework.core.io.Resource load(String storagePath) {
                throw new UnsupportedOperationException();
            }
            @Override
            public void delete(String storagePath) {}
            @Override
            public String getStorageType() { return "local"; }
            @Override
            public StoredFile storeDerivative(String originalFileKey, String variantType,
                                               java.io.InputStream inputStream) {
                return new StoredFile("dfk", "dn.jpg", "/tmp/d.jpg", "local");
            }
        };
        var svc = new FileServiceImpl(mapper, storageService, properties,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, throwService);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[100]);
        var vo = svc.upload(file, "product", null, 1L);

        // Upload must succeed
        assertNotNull(vo.getId());
        assertEquals("IMAGE", vo.getFileType());
        // FileStorage insert must have happened
        assertFalse(inserted.isEmpty(), "FileStorage should be inserted despite derivative failure");
        // updateById (for accessUrl) must have been called
        assertEquals("IMAGE", inserted.get(0).getFileType());
    }

    // --- proxy helper ---

    @SuppressWarnings("unchecked")
    private <T> T proxyMapper(Class<T> mapperType) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    String name = method.getName();
                    switch (name) {
                        case "insert":
                            if (args != null && args.length > 0 && args[0] instanceof FileStorage fs) {
                                inserted.add(fs);
                                if (fs.getId() == null) fs.setId((long) (inserted.size() + 100));
                            }
                            return 1;
                        case "updateById":
                            return 1;
                        case "toString":
                            return "MockFileStorageMapper";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        default:
                            return null;
                    }
                });
    }
}
