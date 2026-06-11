package com.blade.file;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileFolder;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileFolderMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.impl.FileFolderServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileFolderServiceImplTest {

    private FileFolderMapper fileFolderMapper;
    private FileStorageMapper fileStorageMapper;
    private FileFolderServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        initTableInfo(FileFolder.class);
        initTableInfo(FileStorage.class);
        fileFolderMapper = mock(FileFolderMapper.class);
        fileStorageMapper = mock(FileStorageMapper.class);
        service = new FileFolderServiceImpl(fileFolderMapper, fileStorageMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void deleteBlocksWhenFolderDoesNotExist() {
        when(fileFolderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.delete(10L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件夹不存在");

        verify(fileFolderMapper, never()).update(isNull(), any());
        verify(fileStorageMapper, never()).update(isNull(), any());
    }

    @Test
    void deleteBlocksWhenChildFolderExists() {
        when(fileFolderMapper.selectOne(any())).thenReturn(folder(10L, 7L));
        when(fileFolderMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(10L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("请先删除子文件夹");

        verify(fileFolderMapper, never()).update(isNull(), any());
        verify(fileStorageMapper, never()).update(isNull(), any());
    }

    @Test
    void deleteBlocksWhenFilesExistAndMoveIsFalse() {
        when(fileFolderMapper.selectOne(any())).thenReturn(folder(10L, 7L));
        when(fileFolderMapper.selectCount(any())).thenReturn(0L);
        when(fileStorageMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(10L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件夹下存在文件");

        verify(fileFolderMapper, never()).update(isNull(), any());
        verify(fileStorageMapper, never()).update(isNull(), any());
    }

    @Test
    void deleteMovesFilesToUnfiledThenSoftDeletesFolder() {
        when(fileFolderMapper.selectOne(any())).thenReturn(folder(10L, 7L));
        when(fileFolderMapper.selectCount(any())).thenReturn(0L);
        when(fileStorageMapper.selectCount(any())).thenReturn(2L);

        service.delete(10L, true);

        verify(fileStorageMapper).update(isNull(), any(Wrapper.class));
        verify(fileFolderMapper).update(isNull(), any(Wrapper.class));
    }

    private FileFolder folder(Long id, Long tenantId) {
        FileFolder folder = new FileFolder();
        folder.setId(id);
        folder.setTenantId(tenantId);
        folder.setDeleted(0);
        return folder;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}
