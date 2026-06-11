package com.blade.file;

import com.blade.file.controller.FileBindingController;
import com.blade.file.dto.FileBatchDeleteDTO;
import com.blade.file.dto.FileBatchMoveDTO;
import com.blade.file.dto.FileBindingCreateDTO;
import com.blade.file.dto.FileBindingVO;
import com.blade.file.service.FileBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileBindingControllerTest {

    private MockMvc mockMvc;
    private CapturingBindingService bindingService;

    @BeforeEach
    void setUp() {
        bindingService = new CapturingBindingService();
        mockMvc = MockMvcBuilders.standaloneSetup(new FileBindingController(bindingService)).build();
    }

    @Test
    void getBindings_returnsList() throws Exception {
        FileBindingVO vo = new FileBindingVO();
        vo.setId(1L);
        vo.setFileId(100L);
        vo.setBusinessType("product");
        vo.setBusinessId(50L);
        vo.setBindRole("main");
        bindingService.nextBindings = List.of(vo);

        mockMvc.perform(get("/api/files/100/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].businessType").value("product"))
                .andExpect(jsonPath("$.data[0].bindRole").value("main"));

        assertThat(bindingService.capturedFileId).isEqualTo(100L);
    }

    @Test
    void createBindings_passesParams() throws Exception {
        mockMvc.perform(post("/api/files/bindings")
                        .contentType("application/json")
                        .content("{\"fileIds\":[1,2,3],\"businessType\":\"product\",\"businessId\":50,\"bindRole\":\"gallery\",\"isPrimary\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(bindingService.capturedDTO).isNotNull();
        assertThat(bindingService.capturedDTO.getFileIds()).containsExactly(1L, 2L, 3L);
        assertThat(bindingService.capturedDTO.getBusinessType()).isEqualTo("product");
        assertThat(bindingService.capturedDTO.getBusinessId()).isEqualTo(50L);
        assertThat(bindingService.capturedDTO.getBindRole()).isEqualTo("gallery");
        assertThat(bindingService.capturedDTO.getIsPrimary()).isEqualTo(1);
    }

    @Test
    void createBindings_validatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/files/bindings")
                        .contentType("application/json")
                        .content("{\"fileIds\":[],\"businessType\":\"\",\"businessId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBinding_passesId() throws Exception {
        mockMvc.perform(delete("/api/files/bindings/55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(bindingService.capturedBindingId).isEqualTo(55L);
    }

    // ========== 批量操作 ==========

    @Test
    void batchDelete_passesFileIds() throws Exception {
        mockMvc.perform(post("/api/files/batch-delete")
                        .contentType("application/json")
                        .content("{\"fileIds\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(bindingService.capturedBatchDeleteDTO).isNotNull();
        assertThat(bindingService.capturedBatchDeleteDTO.getFileIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void batchDelete_rejectsEmptyFileIds() throws Exception {
        mockMvc.perform(post("/api/files/batch-delete")
                        .contentType("application/json")
                        .content("{\"fileIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchMove_passesParams() throws Exception {
        mockMvc.perform(post("/api/files/batch-move")
                        .contentType("application/json")
                        .content("{\"fileIds\":[1,2],\"folderId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(bindingService.capturedBatchMoveDTO).isNotNull();
        assertThat(bindingService.capturedBatchMoveDTO.getFileIds()).containsExactly(1L, 2L);
        assertThat(bindingService.capturedBatchMoveDTO.getFolderId()).isEqualTo(10L);
    }

    @Test
    void batchMove_allowsNullFolderId() throws Exception {
        mockMvc.perform(post("/api/files/batch-move")
                        .contentType("application/json")
                        .content("{\"fileIds\":[1,2],\"folderId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(bindingService.capturedBatchMoveDTO).isNotNull();
        assertThat(bindingService.capturedBatchMoveDTO.getFileIds()).containsExactly(1L, 2L);
        assertThat(bindingService.capturedBatchMoveDTO.getFolderId()).isNull();
    }

    @Test
    void batchMove_rejectsEmptyFileIds() throws Exception {
        mockMvc.perform(post("/api/files/batch-move")
                        .contentType("application/json")
                        .content("{\"fileIds\":[],\"folderId\":10}"))
                .andExpect(status().isBadRequest());
    }

    // ========== Stub Service ==========

    private static class CapturingBindingService implements FileBindingService {
        private List<FileBindingVO> nextBindings;
        private Long capturedFileId;
        private FileBindingCreateDTO capturedDTO;
        private Long capturedBindingId;
        private FileBatchDeleteDTO capturedBatchDeleteDTO;
        private FileBatchMoveDTO capturedBatchMoveDTO;

        @Override
        public List<FileBindingVO> getBindings(Long fileId) {
            capturedFileId = fileId;
            return nextBindings;
        }

        @Override
        public void createBindings(FileBindingCreateDTO dto) {
            capturedDTO = dto;
        }

        @Override
        public void deleteBinding(Long id) {
            capturedBindingId = id;
        }

        @Override
        public void batchDelete(FileBatchDeleteDTO dto) {
            capturedBatchDeleteDTO = dto;
        }

        @Override
        public void batchMove(FileBatchMoveDTO dto) {
            capturedBatchMoveDTO = dto;
        }
    }
}
