package com.blade.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileCleanupLog;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileCleanupLogMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.impl.FileCleanupServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileCleanupService tests using JDK Proxy mappers.
 * Covers: candidate filtering, soft-delete logging, purge no-physical-delete, empty/no-candidate behavior.
 */
class FileCleanupServiceImplTest {

    @BeforeAll
    static void initMybatisPlus() {
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileStorage.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileBusinessBind.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileCleanupLog.class);
    }

    private MockMapperHandler storageHandler;
    private MockMapperHandler bindHandler;
    private MockMapperHandler logHandler;
    private FileCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        storageHandler = new MockMapperHandler();
        bindHandler = new MockMapperHandler();
        logHandler = new MockMapperHandler();
        FileStorageMapper storageMapper = proxyMapper(FileStorageMapper.class, storageHandler);
        FileBusinessBindMapper bindMapper = proxyMapper(FileBusinessBindMapper.class, bindHandler);
        FileCleanupLogMapper logMapper = proxyMapper(FileCleanupLogMapper.class, logHandler);
        service = new FileCleanupServiceImpl(storageMapper, bindMapper, logMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void countUnboundCandidates_returnsCount() {
        bindHandler.thenSelectList(List.of()); // no bindings found
        storageHandler.thenSelectCount(5L);

        long count = service.countUnboundCandidates(7);
        assertEquals(5L, count);
    }

    @Test
    void countUnboundCandidates_returnsZeroWhenNoCandidates() {
        bindHandler.thenSelectList(List.of());
        storageHandler.thenSelectCount(0L);

        assertEquals(0L, service.countUnboundCandidates(7));
    }

    @Test
    void softDeleteUnbound_updatesAndLogs() {
        bindHandler.thenSelectList(List.of());

        // select candidate file IDs to soft-delete
        FileStorage f1 = new FileStorage();
        f1.setId(100L);
        f1.setStoragePath("uploads/100.jpg");
        f1.setTenantId(1L);
        f1.setStatus(1);
        storageHandler.thenSelectList(List.of(f1));
        storageHandler.nextUpdateRows = 1;

        long count = service.softDeleteUnbound(7);

        assertEquals(1L, count);
        assertTrue(storageHandler.updateLambdaCalled);
        assertTrue(storageHandler.capturedUpdateWrapper.getExpression().getNormal().stream()
                .anyMatch(segment -> segment.getSqlSegment().contains("status")));
        assertNotNull(logHandler.capturedInsertEntity);
        assertEquals("soft_delete_unbound",
                ((FileCleanupLog) logHandler.capturedInsertEntity).getCleanupType());
    }

    @Test
    void softDeleteUnbound_noCandidates_doesNothing() {
        bindHandler.thenSelectList(List.of());
        storageHandler.thenSelectList(List.of());

        assertEquals(0L, service.softDeleteUnbound(7));
        assertNull(logHandler.capturedInsertEntity);
    }

    @Test
    void markPurged_updatesMetadataAndLogs() {
        // select soft-deleted files that qualify for purge marking
        FileStorage f1 = new FileStorage();
        f1.setId(200L);
        f1.setStoragePath("uploads/200.jpg");
        f1.setFileSize(4096L);
        f1.setPurpose("temp");
        f1.setTenantId(1L);
        f1.setStatus(0);

        // First select for unbound check (purpose=temp exempts binding check)
        storageHandler.thenSelectList(List.of(f1));
        storageHandler.nextUpdateRows = 1;

        long count = service.markPurged(30);

        assertEquals(1L, count);
        assertTrue(storageHandler.updateLambdaCalled);
        assertTrue(storageHandler.capturedUpdateWrapper.getExpression().getNormal().stream()
                .anyMatch(segment -> segment.getSqlSegment().contains("status")));
        assertNotNull(logHandler.capturedInsertEntity);
        assertEquals("purge_mark",
                ((FileCleanupLog) logHandler.capturedInsertEntity).getCleanupType());
        assertEquals("uploads/200.jpg",
                ((FileCleanupLog) logHandler.capturedInsertEntity).getStoragePath());
    }

    @Test
    void markPurged_noCandidates_doesNothing() {
        storageHandler.thenSelectList(List.of());

        assertEquals(0L, service.markPurged(30));
        assertNull(logHandler.capturedInsertEntity);
    }

    @Test
    void markPurged_excludesBoundSafePurposeFiles() {
        FileBusinessBind bind = new FileBusinessBind();
        bind.setFileId(200L);
        bindHandler.thenSelectList(List.of(bind));

        FileStorage f1 = new FileStorage();
        f1.setId(200L);
        f1.setStoragePath("uploads/200.jpg");
        f1.setPurpose("temp");
        f1.setTenantId(1L);
        f1.setStatus(0);
        storageHandler.thenSelectList(List.of(f1));

        assertEquals(0L, service.markPurged(30));
        assertFalse(storageHandler.updateLambdaCalled);
        assertNull(logHandler.capturedInsertEntity);
    }

    @Test
    void invalidRetentionDays_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.countUnboundCandidates(0));
        assertThrows(IllegalArgumentException.class, () -> service.softDeleteUnbound(0));
        assertThrows(IllegalArgumentException.class, () -> service.markPurged(0));
    }

    // ==================== Proxy helpers ====================

    @SuppressWarnings("unchecked")
    private <T> T proxyMapper(Class<T> mapperType, MockMapperHandler handler) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                handler);
    }

    private static class MockMapperHandler implements java.lang.reflect.InvocationHandler {
        private final java.util.Queue<Object> selectListResults = new java.util.ArrayDeque<>();
        private final java.util.Queue<Long> selectCountResults = new java.util.ArrayDeque<>();
        private Object capturedInsertEntity;
        private LambdaUpdateWrapper<?> capturedUpdateWrapper;
        private boolean updateLambdaCalled;
        private int nextUpdateRows;

        void thenSelectList(List<?>... results) {
            selectListResults.addAll(List.of(results));
        }

        void thenSelectCount(Long count) {
            selectCountResults.add(count);
        }

        void thenSelectOne(Object result) {
            // not used in these tests
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
                    return null;
                case "insert":
                    if (args != null && args.length > 0) {
                        capturedInsertEntity = args[0];
                    }
                    return 1;
                case "update":
                    if (args != null && args.length >= 2 && args[1] instanceof LambdaUpdateWrapper) {
                        updateLambdaCalled = true;
                        capturedUpdateWrapper = (LambdaUpdateWrapper<?>) args[1];
                    }
                    return nextUpdateRows > 0 ? nextUpdateRows : 1;
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
