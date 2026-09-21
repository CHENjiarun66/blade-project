package com.blade.file;

import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.scheduler.FileCleanupScheduler;
import com.blade.file.service.FileCleanupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FileCleanupScheduler 多租户测试（Series E2 第二轮整改）。
 *
 * <p>定时任务属于系统任务，不带请求租户：必须显式遍历存在文件的 tenant，
 * 每个 tenant try/finally 设置与清理 TenantContext；单租户失败不阻断其他租户；
 * 未显式配置时不得隐式只处理 tenant=1。</p>
 */
class FileCleanupSchedulerTest {

    private FileCleanupService fileCleanupService;
    private FileStorageMapper fileStorageMapper;
    private FileStorageProperties properties;
    private FileCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        fileCleanupService = mock(FileCleanupService.class);
        fileStorageMapper = mock(FileStorageMapper.class);
        properties = new FileStorageProperties();
        scheduler = new FileCleanupScheduler(fileCleanupService, properties, fileStorageMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void defaultConfig_hasNoImplicitTenantOne() {
        assertThat(properties.getCleanup().getTenantId()).isNull();
    }

    @Test
    void iteratesEveryTenantWithFiles_andClearsContextAfterEach() {
        properties.getCleanup().setTenantId(null);
        when(fileStorageMapper.selectDistinctTenantIds()).thenReturn(List.of(1L, 2L));
        List<Long> observed = new ArrayList<>();
        when(fileCleanupService.softDeleteUnbound(anyInt())).thenAnswer(inv -> {
            observed.add(TenantContext.getTenantId());
            return 0L;
        });
        when(fileCleanupService.markPurged(anyInt())).thenAnswer(inv -> {
            observed.add(TenantContext.getTenantId());
            return 0L;
        });

        scheduler.runCleanup();

        // 每个租户依次 softDeleteUnbound + markPurged，且任务内 TenantContext 已按租户设置
        assertThat(observed).containsExactly(1L, 1L, 2L, 2L);
        // 任务结束后不得残留 TenantContext（避免线程复用串租户）
        assertThat(TenantContext.getTenantId()).isNull();
        verify(fileStorageMapper).selectDistinctTenantIds();
    }

    @Test
    void oneTenantFailure_doesNotBlockOtherTenants() {
        properties.getCleanup().setTenantId(null);
        when(fileStorageMapper.selectDistinctTenantIds()).thenReturn(List.of(1L, 2L));
        when(fileCleanupService.softDeleteUnbound(anyInt()))
                .thenThrow(new RuntimeException("tenant 1 boom"))
                .thenReturn(0L);
        when(fileCleanupService.markPurged(anyInt())).thenReturn(0L);

        scheduler.runCleanup();

        verify(fileCleanupService, times(2)).softDeleteUnbound(anyInt());
        // tenant 1 失败不应阻断 tenant 2 的 markPurged
        verify(fileCleanupService, times(1)).markPurged(anyInt());
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void explicitSingleTenant_isUsedAndNoDistinctQuery() {
        properties.getCleanup().setTenantId(7L);
        List<Long> observed = new ArrayList<>();
        when(fileCleanupService.softDeleteUnbound(anyInt())).thenAnswer(inv -> {
            observed.add(TenantContext.getTenantId());
            return 0L;
        });
        when(fileCleanupService.markPurged(anyInt())).thenReturn(0L);

        scheduler.runCleanup();

        assertThat(observed).containsExactly(7L);
        verify(fileStorageMapper, never()).selectDistinctTenantIds();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void noTenantWithFiles_doesNotCallCleanupService() {
        properties.getCleanup().setTenantId(null);
        when(fileStorageMapper.selectDistinctTenantIds()).thenReturn(List.of());

        scheduler.runCleanup();

        verifyNoInteractions(fileCleanupService);
    }
}
