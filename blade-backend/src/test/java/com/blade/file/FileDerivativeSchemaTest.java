package com.blade.file;

import com.blade.file.entity.FileDerivative;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify FileDerivative entity fields exist and are correctly annotated.
 */
class FileDerivativeSchemaTest {

    @Test
    void entity_hasTableAnnotation() {
        var annotation = FileDerivative.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
        assertNotNull(annotation, "FileDerivative must have @TableName");
        assertEquals("file_derivative", annotation.value());
    }

    @Test
    void entity_hasField_fileId() { assertHasField("fileId", Long.class); }
    @Test
    void entity_hasField_variantType() { assertHasField("variantType", String.class); }
    @Test
    void entity_hasField_storageType() { assertHasField("storageType", String.class); }
    @Test
    void entity_hasField_storagePath() { assertHasField("storagePath", String.class); }
    @Test
    void entity_hasField_contentType() { assertHasField("contentType", String.class); }
    @Test
    void entity_hasField_fileSize() { assertHasField("fileSize", Long.class); }
    @Test
    void entity_hasField_width() { assertHasField("width", Integer.class); }
    @Test
    void entity_hasField_height() { assertHasField("height", Integer.class); }
    @Test
    void entity_hasField_status() { assertHasField("status", String.class); }
    @Test
    void entity_hasField_errorMessage() { assertHasField("errorMessage", String.class); }
    @Test
    void entity_hasField_tenantId() { assertHasField("tenantId", Long.class); }
    @Test
    void entity_hasField_createTime() { assertHasField("createTime", java.time.LocalDateTime.class); }
    @Test
    void entity_hasField_updateTime() { assertHasField("updateTime", java.time.LocalDateTime.class); }

    @Test
    void setter_getter_roundTrips() {
        FileDerivative fd = new FileDerivative();
        fd.setId(1L);
        fd.setFileId(100L);
        fd.setVariantType("thumb");
        fd.setStorageType("local");
        fd.setStoragePath("/path/to/file.jpg");
        fd.setContentType("image/jpeg");
        fd.setFileSize(4096L);
        fd.setWidth(320);
        fd.setHeight(240);
        fd.setStatus("READY");
        fd.setErrorMessage(null);
        fd.setTenantId(1L);

        assertEquals(1L, fd.getId());
        assertEquals(100L, fd.getFileId());
        assertEquals("thumb", fd.getVariantType());
        assertEquals("local", fd.getStorageType());
        assertEquals("/path/to/file.jpg", fd.getStoragePath());
        assertEquals("image/jpeg", fd.getContentType());
        assertEquals(4096L, fd.getFileSize());
        assertEquals(320, fd.getWidth());
        assertEquals(240, fd.getHeight());
        assertEquals("READY", fd.getStatus());
        assertNull(fd.getErrorMessage());
        assertEquals(1L, fd.getTenantId());
    }

    private static void assertHasField(String fieldName, Class<?> type) {
        try {
            Field field = FileDerivative.class.getDeclaredField(fieldName);
            assertEquals(type, field.getType(), "Field " + fieldName + " has wrong type");
        } catch (NoSuchFieldException e) {
            fail("FileDerivative missing field: " + fieldName);
        }
    }
}
