package com.blade.file;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileCleanupLog;
import com.blade.file.entity.FileFolder;
import com.blade.file.entity.FileOperationLog;
import com.blade.file.entity.FileStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: 验证 BE-1001 数字资产表结构扩展的实体和字段存在。
 * 先写测试，再补实体 — 首次运行因缺字段/类而失败。
 */
class FileAssetSchemaTest {

    // ==================== FileStorage 新字段 ====================

    @Test
    void fileStorage_hasField_folderId() {
        assertHasField(FileStorage.class, "folderId", Long.class);
    }

    @Test
    void fileStorage_hasField_fileType() {
        assertHasField(FileStorage.class, "fileType", String.class);
    }

    @Test
    void fileStorage_hasField_fileExt() {
        assertHasField(FileStorage.class, "fileExt", String.class);
    }

    @Test
    void fileStorage_hasField_fileHash() {
        assertHasField(FileStorage.class, "fileHash", String.class);
    }

    @Test
    void fileStorage_hasField_source() {
        assertHasField(FileStorage.class, "source", String.class);
    }

    @Test
    void fileStorage_hasField_purpose() {
        assertHasField(FileStorage.class, "purpose", String.class);
    }

    @Test
    void fileStorage_hasField_bindCount() {
        assertHasField(FileStorage.class, "bindCount", Integer.class);
    }

    @Test
    void fileStorage_hasField_visibility() {
        assertHasField(FileStorage.class, "visibility", String.class);
    }

    @Test
    void fileStorage_hasField_imageWidth() {
        assertHasField(FileStorage.class, "imageWidth", Integer.class);
    }

    @Test
    void fileStorage_hasField_imageHeight() {
        assertHasField(FileStorage.class, "imageHeight", Integer.class);
    }

    @Test
    void fileStorage_hasField_durationSeconds() {
        assertHasField(FileStorage.class, "durationSeconds", Integer.class);
    }

    @Test
    void fileStorage_hasField_coverFileId() {
        assertHasField(FileStorage.class, "coverFileId", Long.class);
    }

    @Test
    void fileStorage_hasField_deletedTime() {
        assertHasField(FileStorage.class, "deletedTime", LocalDateTime.class);
    }

    @Test
    void fileStorage_hasField_purgedTime() {
        assertHasField(FileStorage.class, "purgedTime", LocalDateTime.class);
    }

    // ==================== 新增实体和 @TableName ====================

    @Test
    void fileFolder_existsWithTableName() {
        assertTableName(FileFolder.class, "file_folder");
    }

    @Test
    void fileFolder_hasFields() {
        assertHasField(FileFolder.class, "parentId", Long.class);
        assertHasField(FileFolder.class, "folderName", String.class);
        assertHasField(FileFolder.class, "sort", Integer.class);
        assertHasField(FileFolder.class, "tenantId", Long.class);
        assertHasField(FileFolder.class, "createBy", Long.class);
        assertHasField(FileFolder.class, "deleted", Integer.class);
    }

    @Test
    void fileBusinessBind_existsWithTableName() {
        assertTableName(FileBusinessBind.class, "file_business_bind");
    }

    @Test
    void fileBusinessBind_hasFields() {
        assertHasField(FileBusinessBind.class, "fileId", Long.class);
        assertHasField(FileBusinessBind.class, "businessType", String.class);
        assertHasField(FileBusinessBind.class, "businessId", Long.class);
        assertHasField(FileBusinessBind.class, "bindRole", String.class);
        assertHasField(FileBusinessBind.class, "sort", Integer.class);
        assertHasField(FileBusinessBind.class, "isPrimary", Integer.class);
        assertHasField(FileBusinessBind.class, "tenantId", Long.class);
        assertHasField(FileBusinessBind.class, "createBy", Long.class);
        assertHasField(FileBusinessBind.class, "deleted", Integer.class);
    }

    @Test
    void fileOperationLog_existsWithTableName() {
        assertTableName(FileOperationLog.class, "file_operation_log");
    }

    @Test
    void fileOperationLog_hasFields() {
        assertHasField(FileOperationLog.class, "fileId", Long.class);
        assertHasField(FileOperationLog.class, "operationType", String.class);
        assertHasField(FileOperationLog.class, "detail", String.class);
        assertHasField(FileOperationLog.class, "operatorId", Long.class);
        assertHasField(FileOperationLog.class, "tenantId", Long.class);
    }

    @Test
    void fileCleanupLog_existsWithTableName() {
        assertTableName(FileCleanupLog.class, "file_cleanup_log");
    }

    @Test
    void fileCleanupLog_hasFields() {
        assertHasField(FileCleanupLog.class, "fileId", Long.class);
        assertHasField(FileCleanupLog.class, "cleanupType", String.class);
        assertHasField(FileCleanupLog.class, "storagePath", String.class);
        assertHasField(FileCleanupLog.class, "fileSize", Long.class);
        assertHasField(FileCleanupLog.class, "reason", String.class);
        assertHasField(FileCleanupLog.class, "operatorId", Long.class);
        assertHasField(FileCleanupLog.class, "tenantId", Long.class);
    }

    // ==================== 辅助方法 ====================

    private void assertHasField(Class<?> clazz, String fieldName, Class<?> expectedType) {
        Field field = assertDoesNotThrow(() -> clazz.getDeclaredField(fieldName),
                () -> "Field '" + fieldName + "' should exist in " + clazz.getSimpleName());
        assertEquals(expectedType, field.getType(),
                "Field '" + fieldName + "' in " + clazz.getSimpleName()
                        + " should be of type " + expectedType.getSimpleName());
    }

    private void assertTableName(Class<?> entityClass, String expectedTable) {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        assertNotNull(annotation,
                entityClass.getSimpleName() + " should be annotated with @TableName");
        assertEquals(expectedTable, annotation.value(),
                entityClass.getSimpleName() + " @TableName should be '" + expectedTable + "'");
    }
}
