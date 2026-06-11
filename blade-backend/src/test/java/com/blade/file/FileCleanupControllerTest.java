package com.blade.file;

import com.blade.file.controller.FileCleanupController;
import com.blade.file.service.FileCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileCleanupControllerTest {

    private MockMvc mockMvc;
    private CapturingCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new CapturingCleanupService();
        mockMvc = MockMvcBuilders.standaloneSetup(new FileCleanupController(cleanupService)).build();
    }

    @Test
    void getUnboundCandidates_passesDaysAndReturnsJson() throws Exception {
        mockMvc.perform(get("/api/files/cleanup/unbound-candidates")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.candidateCount").value(3))
                .andExpect(jsonPath("$.data.retentionDays").value(7));

        assertThat(cleanupService.capturedUnboundDays).isEqualTo(7);
    }

    @Test
    void getUnboundCandidates_defaultsDays() throws Exception {
        mockMvc.perform(get("/api/files/cleanup/unbound-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.retentionDays").value(7));

        assertThat(cleanupService.capturedUnboundDays).isEqualTo(7);
    }

    @Test
    void softDeleteUnbound_passesDays() throws Exception {
        mockMvc.perform(post("/api/files/cleanup/soft-delete-unbound")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processedCount").value(3));

        assertThat(cleanupService.capturedSoftDeleteDays).isEqualTo(7);
    }

    @Test
    void softDeleteUnbound_defaultsDays() throws Exception {
        mockMvc.perform(post("/api/files/cleanup/soft-delete-unbound"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.retentionDays").value(7));

        assertThat(cleanupService.capturedSoftDeleteDays).isEqualTo(7);
    }

    @Test
    void markPurged_passesDays() throws Exception {
        mockMvc.perform(post("/api/files/cleanup/mark-purged")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processedCount").value(2));

        assertThat(cleanupService.capturedPurgeDays).isEqualTo(30);
    }

    @Test
    void markPurged_defaultsDays() throws Exception {
        mockMvc.perform(post("/api/files/cleanup/mark-purged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.retentionDays").value(30));

        assertThat(cleanupService.capturedPurgeDays).isEqualTo(30);
    }

    private static class CapturingCleanupService implements FileCleanupService {
        private int capturedUnboundDays;
        private int capturedSoftDeleteDays;
        private int capturedPurgeDays;

        @Override
        public long softDeleteUnbound(int retentionDays) {
            capturedSoftDeleteDays = retentionDays;
            return 3;
        }

        @Override
        public long markPurged(int retentionDays) {
            capturedPurgeDays = retentionDays;
            return 2;
        }

        @Override
        public long countUnboundCandidates(int retentionDays) {
            capturedUnboundDays = retentionDays;
            return 3;
        }
    }
}
