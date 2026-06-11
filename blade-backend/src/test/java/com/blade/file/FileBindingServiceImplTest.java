package com.blade.file;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.dto.FileBatchDeleteDTO;
import com.blade.file.dto.FileBatchMoveDTO;
import com.blade.file.dto.FileBindingCreateDTO;
import com.blade.file.dto.FileBindingVO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileFolder;
import com.blade.file.entity.FileOperationLog;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileFolderMapper;
import com.blade.file.mapper.FileOperationLogMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.impl.FileBindingServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileBindingService 测试 — 使用 JDK Proxy mock mapper。
 * 覆盖：绑定前文件校验、解绑不存在、批量删除过滤、移动文件夹不存在。
 */
class FileBindingServiceImplTest {

    @BeforeAll
    static void initMybatisPlus() {
        // 初始化 MyBatis-Plus 实体反射缓存
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileStorage.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileBusinessBind.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileOperationLog.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileFolder.class);
    }

    private MockMapperHandler storageHandler;
    private MockMapperHandler bindHandler;
    private MockMapperHandler logHandler;
    private MockMapperHandler folderHandler;
    private FileBindingServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        storageHandler = new MockMapperHandler();
        bindHandler = new MockMapperHandler();
        logHandler = new MockMapperHandler();
        folderHandler = new MockMapperHandler();
        FileStorageMapper storageMapper = proxyMapper(FileStorageMapper.class, storageHandler);
        FileBusinessBindMapper bindMapper = proxyMapper(FileBusinessBindMapper.class, bindHandler);
        FileOperationLogMapper logMapper = proxyMapper(FileOperationLogMapper.class, logHandler);
        FileFolderMapper folderMapper = proxyMapper(FileFolderMapper.class, folderHandler);
        service = new FileBindingServiceImpl(storageMapper, bindMapper, logMapper, folderMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== getBindings ====================

    @Test
    void getBindings_returnsActiveBindings() {
        FileBusinessBind b1 = new FileBusinessBind();
        b1.setId(1L);
        b1.setFileId(100L);
        b1.setBusinessType("product");
        b1.setBusinessId(50L);
        b1.setBindRole("main");
        b1.setSort(0);
        b1.setIsPrimary(1);
        b1.setDeleted(0);
        bindHandler.thenSelectList(List.of(b1));

        List<FileBindingVO> result = service.getBindings(100L);

        assertEquals(1, result.size());
        assertEquals("product", result.get(0).getBusinessType());
        assertEquals("main", result.get(0).getBindRole());
        assertEquals(1, result.get(0).getIsPrimary().intValue());
    }

    @Test
    void getBindings_returnsEmpty_whenNone() {
        bindHandler.thenSelectList(List.of());
        assertTrue(service.getBindings(100L).isEmpty());
    }

    // ==================== createBindings ====================

    @Test
    void createBindings_throwsWhenFileNotFound() {
        storageHandler.thenSelectCount(0L); // 文件不存在

        FileBindingCreateDTO dto = new FileBindingCreateDTO();
        dto.setFileIds(List.of(999L));
        dto.setBusinessType("product");
        dto.setBusinessId(1L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createBindings(dto));
        assertEquals("文件不存在", ex.getMessage());
    }

    @Test
    void createBindings_insertsBindingsAndLog() {
        storageHandler.thenSelectCount(3L); // 3 个文件都存在
        bindHandler.thenInsert(1); // 插入成功

        FileBindingCreateDTO dto = new FileBindingCreateDTO();
        dto.setFileIds(List.of(10L, 20L, 30L));
        dto.setBusinessType("product");
        dto.setBusinessId(5L);
        dto.setBindRole("gallery");
        dto.setIsPrimary(0);

        service.createBindings(dto);

        // 验证写入操作日志
        assertNotNull(logHandler.capturedInsertEntity);
        assertEquals("bind", ((FileOperationLog) logHandler.capturedInsertEntity).getOperationType());
    }

    @Test
    void createBindings_throwsWhenNonNullDtoFieldIsBlank() {
        // businessType blank
        FileBindingCreateDTO dto = new FileBindingCreateDTO();
        dto.setFileIds(List.of(1L));
        dto.setBusinessType("");
        dto.setBusinessId(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createBindings(dto));
        assertEquals("businessType和businessId不能为空", ex.getMessage());
    }

    // ==================== deleteBinding ====================

    @Test
    void deleteBinding_throwsWhenNotFound() {
        bindHandler.thenSelectOne(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteBinding(999L));
        assertEquals("绑定不存在", ex.getMessage());
    }

    @Test
    void deleteBinding_softDeletesAndLogs() {
        FileBusinessBind bind = new FileBusinessBind();
        bind.setId(5L);
        bind.setFileId(100L);
        bind.setTenantId(1L);
        bind.setDeleted(0);
        bindHandler.thenSelectOne(bind);
        bindHandler.nextUpdateRows = 1;

        service.deleteBinding(5L);

        // 验证软删除
        assertTrue(bindHandler.updateLambdaCalled);
        // 验证写入操作日志
        assertNotNull(logHandler.capturedInsertEntity);
        assertEquals("unbind", ((FileOperationLog) logHandler.capturedInsertEntity).getOperationType());
    }

    // ==================== 批量删除 (FileService 方法，通过 FileBindingServiceImpl 代理) ====================

    @Test
    void batchDelete_updatesStatus() {
        bindHandler.thenSelectList(List.of());
        storageHandler.nextUpdateRows = 3;

        FileBatchDeleteDTO dto = new FileBatchDeleteDTO();
        dto.setFileIds(List.of(1L, 2L, 3L));

        // 由 FileBindingServiceImpl 转发给 FileStorageMapper
        service.batchDelete(dto);

        assertTrue(storageHandler.updateLambdaCalled);
        // 验证操作日志
        assertNotNull(logHandler.capturedInsertEntity);
        assertEquals("batch_delete", ((FileOperationLog) logHandler.capturedInsertEntity).getOperationType());
    }

    @Test
    void batchDelete_rejectsActiveBoundFiles() {
        FileBusinessBind bind = new FileBusinessBind();
        bind.setFileId(2L);
        bind.setTenantId(1L);
        bind.setDeleted(0);
        bindHandler.thenSelectList(List.of(bind));

        FileBatchDeleteDTO dto = new FileBatchDeleteDTO();
        dto.setFileIds(List.of(1L, 2L, 3L));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.batchDelete(dto));

        assertEquals("文件存在有效绑定，不能删除", ex.getMessage());
        assertFalse(storageHandler.updateLambdaCalled);
        assertNull(logHandler.capturedInsertEntity);
    }

    @Test
    void batchDelete_emptyIds_doesNothing() {
        FileBatchDeleteDTO dto = new FileBatchDeleteDTO();
        dto.setFileIds(List.of());
        service.batchDelete(dto);
        // 不应调用任何 mapper
        assertNull(logHandler.capturedInsertEntity);
    }

    // ==================== 批量移动 ====================

    @Test
    void batchMove_throwsWhenFolderNotFound() {
        folderHandler.thenSelectOne(null);

        FileBatchMoveDTO dto = new FileBatchMoveDTO();
        dto.setFileIds(List.of(1L));
        dto.setFolderId(999L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.batchMove(dto));
        assertEquals("文件夹不存在", ex.getMessage());
    }

    @Test
    void batchMove_nullFolderId_updatesDirectly() {
        FileBatchMoveDTO dto = new FileBatchMoveDTO();
        dto.setFileIds(List.of(1L, 2L));
        dto.setFolderId(null);

        service.batchMove(dto);

        assertTrue(storageHandler.updateLambdaCalled);
        assertNotNull(logHandler.capturedInsertEntity);
        assertEquals("batch_move", ((FileOperationLog) logHandler.capturedInsertEntity).getOperationType());
    }

    @Test
    void batchMove_withFolderId_validatesThenUpdates() {
        // 文件夹存在
        FileFolder folder = new FileFolder();
        folder.setId(10L);
        folder.setTenantId(1L);
        folder.setDeleted(0);
        folderHandler.thenSelectOne(folder);

        FileBatchMoveDTO dto = new FileBatchMoveDTO();
        dto.setFileIds(List.of(1L, 2L, 3L));
        dto.setFolderId(10L);

        service.batchMove(dto);

        assertTrue(storageHandler.updateLambdaCalled);
        assertNotNull(logHandler.capturedInsertEntity);
        assertEquals("batch_move", ((FileOperationLog) logHandler.capturedInsertEntity).getOperationType());
    }

    // ==================== Proxy 辅助 ====================

    @SuppressWarnings("unchecked")
    private <T> T proxyMapper(Class<T> mapperType, MockMapperHandler handler) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                handler);
    }

    private static class MockMapperHandler implements java.lang.reflect.InvocationHandler {
        private final Queue<Object> selectListResults = new ArrayDeque<>();
        private final Queue<Long> selectCountResults = new ArrayDeque<>();
        private Object nextSelectOne;
        private int nextInsertRows;
        private int nextUpdateRows;
        private Object capturedInsertEntity;
        private boolean updateLambdaCalled;

        void thenSelectList(List<?>... results) {
            selectListResults.addAll(List.of(results));
        }

        void thenSelectCount(Long count) {
            selectCountResults.add(count);
        }

        void thenSelectOne(Object result) {
            nextSelectOne = result;
        }

        void thenInsert(int rows) {
            nextInsertRows = rows;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            String name = method.getName();
            switch (name) {
                case "selectList":
                    return selectListResults.isEmpty() ? List.of() : selectListResults.remove();
                case "selectCount":
                    if (!selectCountResults.isEmpty()) return selectCountResults.remove();
                    return 0L;
                case "selectOne":
                    return nextSelectOne;
                case "insert":
                    if (args != null && args.length > 0) {
                        capturedInsertEntity = args[0];
                    }
                    return nextInsertRows > 0 ? nextInsertRows : 1;
                case "update":
                    // detect LambdaUpdateWrapper usage: entity is null, updateWrapper has conditions
                    if (args != null && args.length >= 2 && args[1] instanceof LambdaUpdateWrapper) {
                        updateLambdaCalled = true;
                        // If entity arg is not null, it's a normal entity update
                        if (args[0] != null) {
                            capturedInsertEntity = null; // not a log insert
                        }
                    }
                    return nextUpdateRows > 0 ? nextUpdateRows : (updateLambdaCalled ? 1 : 0);
                case "toString":
                    return "MockMapper";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    Class<?> retType = method.getReturnType();
                    if (!retType.isPrimitive()) return null;
                    if (retType == boolean.class) return false;
                    if (retType == void.class) return null;
                    return 0;
            }
        }
    }
}
