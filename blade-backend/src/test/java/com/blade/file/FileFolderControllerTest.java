package com.blade.file;

import com.blade.file.controller.FileFolderController;
import com.blade.file.dto.FileFolderCreateDTO;
import com.blade.file.dto.FileFolderUpdateDTO;
import com.blade.file.dto.FileFolderVO;
import com.blade.file.service.FileFolderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileFolderControllerTest {

    private MockMvc mockMvc;
    private CapturingFileFolderService folderService;

    @BeforeEach
    void setUp() {
        folderService = new CapturingFileFolderService();
        mockMvc = MockMvcBuilders.standaloneSetup(new FileFolderController(folderService)).build();
    }

    @Test
    void getTree_returnsFolderTree() throws Exception {
        folderService.nextTree = List.of(
                folderVO(1L, null, "根文件夹", 0),
                folderVO(2L, 1L, "子文件夹", 0)
        );

        mockMvc.perform(get("/api/file-folders/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].folderName").value("根文件夹"));
    }

    @Test
    void createFolder_returnsFolderId() throws Exception {
        folderService.nextId = 101L;

        mockMvc.perform(post("/api/file-folders")
                        .contentType("application/json")
                        .content("{\"folderName\":\"新文件夹\",\"parentId\":1,\"sort\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(101));

        assertThat(folderService.capturedCreateDTO).isNotNull();
        assertThat(folderService.capturedCreateDTO.getFolderName()).isEqualTo("新文件夹");
        assertThat(folderService.capturedCreateDTO.getParentId()).isEqualTo(1L);
        assertThat(folderService.capturedCreateDTO.getSort()).isEqualTo(3);
    }

    @Test
    void createFolder_validatesFolderName() throws Exception {
        mockMvc.perform(post("/api/file-folders")
                        .contentType("application/json")
                        .content("{\"folderName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFolder_passesParams() throws Exception {
        mockMvc.perform(put("/api/file-folders/5")
                        .contentType("application/json")
                        .content("{\"folderName\":\"重命名\",\"parentId\":2,\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(folderService.capturedUpdateId).isEqualTo(5L);
        assertThat(folderService.capturedUpdateDTO).isNotNull();
        assertThat(folderService.capturedUpdateDTO.getFolderName()).isEqualTo("重命名");
        assertThat(folderService.capturedUpdateDTO.getParentId()).isEqualTo(2L);
        assertThat(folderService.capturedUpdateDTO.getSort()).isEqualTo(1);
    }

    @Test
    void deleteFolder_withoutMoveParam() throws Exception {
        mockMvc.perform(delete("/api/file-folders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(folderService.capturedDeleteId).isEqualTo(10L);
        assertThat(folderService.capturedMoveFilesToUnfiled).isFalse();
    }

    @Test
    void deleteFolder_withMoveFilesTrue() throws Exception {
        mockMvc.perform(delete("/api/file-folders/10")
                        .param("moveFilesToUnfiled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(folderService.capturedDeleteId).isEqualTo(10L);
        assertThat(folderService.capturedMoveFilesToUnfiled).isTrue();
    }

    @Test
    void deleteFolder_withMoveFilesFalse() throws Exception {
        mockMvc.perform(delete("/api/file-folders/10")
                        .param("moveFilesToUnfiled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(folderService.capturedDeleteId).isEqualTo(10L);
        assertThat(folderService.capturedMoveFilesToUnfiled).isFalse();
    }

    // === 辅助 ===

    private FileFolderVO folderVO(Long id, Long parentId, String name, int sort) {
        FileFolderVO vo = new FileFolderVO();
        vo.setId(id);
        vo.setParentId(parentId);
        vo.setFolderName(name);
        vo.setSort(sort);
        vo.setChildren(List.of());
        return vo;
    }

    // === Stub ===

    private static class CapturingFileFolderService implements FileFolderService {
        private List<FileFolderVO> nextTree;
        private Long nextId;
        private FileFolderCreateDTO capturedCreateDTO;
        private Long capturedUpdateId;
        private FileFolderUpdateDTO capturedUpdateDTO;
        private Long capturedDeleteId;
        private boolean capturedMoveFilesToUnfiled;

        @Override
        public List<FileFolderVO> getTree() {
            return nextTree;
        }

        @Override
        public Long create(FileFolderCreateDTO dto, Long operatorId) {
            capturedCreateDTO = dto;
            return nextId;
        }

        @Override
        public void update(Long id, FileFolderUpdateDTO dto) {
            capturedUpdateId = id;
            capturedUpdateDTO = dto;
        }

        @Override
        public void delete(Long id, boolean moveFilesToUnfiled) {
            capturedDeleteId = id;
            capturedMoveFilesToUnfiled = moveFilesToUnfiled;
        }
    }
}
