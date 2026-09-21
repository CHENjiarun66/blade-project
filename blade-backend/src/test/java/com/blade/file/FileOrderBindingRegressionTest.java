package com.blade.file;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileDerivativeService;
import com.blade.file.service.impl.FileServiceImpl;
import com.blade.file.storage.FileStorageService;
import com.blade.file.storage.StoredFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileOrderBindingRegressionTest {

    @Mock private FileStorageMapper fileStorageMapper;
    @Mock private FileBusinessBindMapper fileBusinessBindMapper;
    @Mock private FileStorageService storageService;
    @Mock private FileDerivativeService derivativeService;
    @Mock private com.blade.file.policy.FileBusinessAccessPolicy fileBusinessAccessPolicy;

    private FileServiceImpl service;

    @BeforeAll
    static void initMybatisPlus() {
        var config = new org.apache.ibatis.session.Configuration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("file-order-binding-regression");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileStorage.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileBusinessBind.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxSizeMb(20L);
        properties.setPreviewUrlPrefix("/api/files");
        service = new FileServiceImpl(
                fileStorageMapper,
                storageService,
                properties,
                new ObjectMapper(),
                fileBusinessBindMapper,
                derivativeService,
                fileBusinessAccessPolicy);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadWithExistingOrderIdCreatesCanonicalBindingImmediately() {
        when(storageService.store(any(), eq("order")))
                .thenReturn(new StoredFile("key", "order.jpg", "/tmp/order.jpg", "local"));
        doAnswer(invocation -> {
            FileStorage entity = invocation.getArgument(0);
            entity.setId(501L);
            return 1;
        }).when(fileStorageMapper).insert(any(FileStorage.class));
        when(fileStorageMapper.selectCount(any())).thenReturn(1L);
        when(fileBusinessBindMapper.selectCount(any())).thenReturn(0L);

        service.upload(new MockMultipartFile(
                "file", "order.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                "order", 42L, 7L);

        ArgumentCaptor<FileBusinessBind> bindCaptor = ArgumentCaptor.forClass(FileBusinessBind.class);
        verify(fileBusinessBindMapper).insert(bindCaptor.capture());
        FileBusinessBind bind = bindCaptor.getValue();
        assertThat(bind.getFileId()).isEqualTo(501L);
        assertThat(bind.getBusinessType()).isEqualTo("order");
        assertThat(bind.getBusinessId()).isEqualTo(42L);
        assertThat(bind.getTenantId()).isEqualTo(1L);
        assertThat(bind.getDeleted()).isZero();
    }

    @Test
    void orderImageCategoryIncludesFormalOrdersAndDrafts() {
        ArgumentCaptor<Wrapper<FileBusinessBind>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        when(fileBusinessBindMapper.selectList(wrapperCaptor.capture())).thenReturn(List.of());

        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setBusinessType("order");

        service.pageList(dto);

        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<FileBusinessBind> captured =
                (LambdaQueryWrapper<FileBusinessBind>) wrapperCaptor.getValue();
        String sql = captured.getSqlSegment();
        assertThat(sql).contains("business_type IN");
        assertThat(captured.getParamNameValuePairs().values())
                .contains("order", "order_draft");
        verify(fileStorageMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void jsonImageSetUsesSyncSoRemovedBindingsAreSoftDeleted() {
        FileBusinessBind removed = new FileBusinessBind();
        removed.setId(71L);
        removed.setFileId(900L);
        removed.setBusinessType("order");
        removed.setBusinessId(42L);
        removed.setTenantId(1L);
        removed.setDeleted(0);
        when(fileBusinessBindMapper.selectList(any())).thenReturn(List.of(removed));

        service.syncFilesFromJson("order", 42L, "[]");

        verify(fileBusinessBindMapper).update(isNull(), any());
    }

    @Test
    void v61MigrationBackfillsOrderAndDraftBindingsWithoutDestructiveSql() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V61__repair_order_file_bindings.sql"));

        assertThat(sql)
                .contains("JOIN JSON_TABLE")
                .contains("'order'")
                .contains("'order_draft'")
                .contains("existing.`id` IS NULL")
                .doesNotContain("DELETE FROM", "TRUNCATE", "DROP TABLE", "DROP COLUMN");
    }
}
