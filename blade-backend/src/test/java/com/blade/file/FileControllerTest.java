package com.blade.file;

import com.blade.common.exception.GlobalExceptionHandler;
import com.blade.common.result.PageResult;
import com.blade.file.controller.FileController;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.dto.FileUploadVO;
import com.blade.file.dto.FileVO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private MockMvc mockMvc;
    private CapturingFileService fileService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        fileService = new CapturingFileService();
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // === 原有上传测试 ===

    @Test
    void uploadReturnsFileIdAndPreviewUrl() throws Exception {
        fileService.nextUpload = uploadVO(101L, "order.png", "image/png", 4L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "order.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("businessType", "order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.url").value("/api/files/101/preview"))
                .andExpect(jsonPath("$.data.fileType").value("IMAGE"))
                .andExpect(jsonPath("$.data.fileExt").value("png"));

        assertThat(fileService.capturedFile.getOriginalFilename()).isEqualTo("order.png");
        assertThat(fileService.capturedBusinessType).isEqualTo("order");
        assertThat(fileService.capturedBusinessId).isNull();
        assertThat(fileService.capturedOperatorId).isEqualTo(1L);
    }

    @Test
    void uploadVideoReturnsFileTypeAndFileExt() throws Exception {
        fileService.nextUpload = uploadVO(103L, "demo.mp4", "video/mp4", 8L, "VIDEO", "mp4");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.mp4",
                "video/mp4",
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("businessType", "product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(103))
                .andExpect(jsonPath("$.data.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.data.fileType").value("VIDEO"))
                .andExpect(jsonPath("$.data.fileExt").value("mp4"));

        assertThat(fileService.capturedFile.getOriginalFilename()).isEqualTo("demo.mp4");
        assertThat(fileService.capturedFile.getContentType()).isEqualTo("video/mp4");
        assertThat(fileService.capturedBusinessType).isEqualTo("product");
        assertThat(fileService.capturedOperatorId).isEqualTo(1L);
    }

    @Test
    void uploadPassesBusinessIdWhenProvided() throws Exception {
        fileService.nextUpload = uploadVO(102L, "product.jpg", "image/jpeg", 3L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("businessType", "product")
                        .param("businessId", "88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(102));

        assertThat(fileService.capturedBusinessType).isEqualTo("product");
        assertThat(fileService.capturedBusinessId).isEqualTo(88L);
        assertThat(fileService.capturedOperatorId).isEqualTo(1L);
    }

    // === BE-1002 新测试：分页列表 ===

    @Test
    void pageList_returnsPageResult() throws Exception {
        FileVO vo = fileVO(101L, "test.png");
        fileService.nextPageResult = PageResult.of(List.of(vo), 1, 20, 1);

        mockMvc.perform(get("/api/files")
                        .param("current", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(101))
                .andExpect(jsonPath("$.data.records[0].originalName").value("test.png"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void pageList_passesFilterParams() throws Exception {
        fileService.nextPageResult = PageResult.of(List.of(), 0, 20, 1);

        mockMvc.perform(get("/api/files")
                        .param("keyword", "hello")
                        .param("folderId", "5")
                        .param("fileType", "IMAGE")
                        .param("businessType", "product")
                        .param("bound", "true")
                        .param("purpose", "main")
                        .param("createBy", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-06-30")
                        .param("status", "1"))
                .andExpect(status().isOk());

        assertThat(fileService.capturedPageDTO).isNotNull();
        assertThat(fileService.capturedPageDTO.getKeyword()).isEqualTo("hello");
        assertThat(fileService.capturedPageDTO.getFolderId()).isEqualTo(5L);
        assertThat(fileService.capturedPageDTO.getFileType()).isEqualTo("IMAGE");
        assertThat(fileService.capturedPageDTO.getBusinessType()).isEqualTo("product");
        assertThat(fileService.capturedPageDTO.getBound()).isTrue();
        assertThat(fileService.capturedPageDTO.getPurpose()).isEqualTo("main");
        assertThat(fileService.capturedPageDTO.getCreateBy()).isEqualTo(1L);
        assertThat(fileService.capturedPageDTO.getStartDate()).isEqualTo("2026-01-01");
        assertThat(fileService.capturedPageDTO.getEndDate()).isEqualTo("2026-06-30");
        assertThat(fileService.capturedPageDTO.getStatus()).isEqualTo(1);
    }

    @Test
    void pageList_defaultPageParams() throws Exception {
        fileService.nextPageResult = PageResult.of(List.of(), 0, 20, 1);

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk());

        assertThat(fileService.capturedPageDTO.getCurrent()).isEqualTo(1L);
        assertThat(fileService.capturedPageDTO.getSize()).isEqualTo(20L);
    }

    // === BE-1002 新测试：文件详情 ===

    @Test
    void getDetail_returnsFileDetail() throws Exception {
        FileVO vo = fileVO(101L, "detail.jpg");
        fileService.nextDetail = vo;

        mockMvc.perform(get("/api/files/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.originalName").value("detail.jpg"))
                .andExpect(jsonPath("$.data.fileType").value("IMAGE"));

        assertThat(fileService.capturedDetailId).isEqualTo(101L);
    }

    // === BE-1009A 测试：文件预览访问控制 ===

    @Test
    void previewPublicSucceedsWithoutAuthentication() throws Exception {
        fileService.nextActiveFile = fileStorage(201L, "PUBLIC", "image/png");
        fileService.nextResource = new ByteArrayResource(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/files/201/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));

        assertThat(fileService.capturedPreviewId).isEqualTo(201L);
    }

    @Test
    void previewPrivateDeniedWithoutAuthentication() throws Exception {
        fileService.nextActiveFile = fileStorage(202L, "PRIVATE", "image/png");

        mockMvc.perform(get("/api/files/202/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void previewNullVisibilityDeniedWithoutAuthentication() throws Exception {
        fileService.nextActiveFile = fileStorage(203L, null, "image/png");

        mockMvc.perform(get("/api/files/203/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void previewPrivateSucceedsWithAuthenticatedUser() throws Exception {
        // BE-1009B: 需要 btn:file:viewAll 权限才能通过业务权限校验
        User user = new User("admin", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("btn:file:viewAll"))));

        fileService.nextActiveFile = fileStorage(204L, "PRIVATE", "image/jpeg");
        fileService.nextResource = new ByteArrayResource(new byte[]{4, 5, 6});

        mockMvc.perform(get("/api/files/204/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        assertThat(fileService.capturedPreviewId).isEqualTo(204L);
    }

    // === BE-1009B 测试：业务权限映射 ===

    @Test
    void previewPrivateOrderFileWithOrderViewPermissionSucceeds() throws Exception {
        // PRIVATE order 文件 + btn:order:view → 成功
        User user = new User("admin", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("btn:order:view"))));

        fileService.nextActiveFile = fileStorage(301L, "PRIVATE", "image/png");
        fileService.nextBindings = List.of(binding(301L, "order"));
        fileService.nextResource = new ByteArrayResource(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/files/301/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void previewPrivateOrderFileWithoutOrderViewPermissionFails() throws Exception {
        // PRIVATE order 文件，没有 btn:order:view → 403
        User user = new User("admin", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("menu:product"))));

        fileService.nextActiveFile = fileStorage(302L, "PRIVATE", "image/png");
        fileService.nextBindings = List.of(binding(302L, "order"));

        mockMvc.perform(get("/api/files/302/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void previewPrivateProductFileWithMenuProductPermissionSucceeds() throws Exception {
        // PRIVATE product 文件 + menu:product → 成功
        User user = new User("admin", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("menu:product"))));

        fileService.nextActiveFile = fileStorage(303L, "PRIVATE", "image/jpeg");
        fileService.nextBindings = List.of(binding(303L, "product"));
        fileService.nextResource = new ByteArrayResource(new byte[]{7, 8, 9});

        mockMvc.perform(get("/api/files/303/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    void previewPrivateProductFileWithoutMenuProductPermissionFails() throws Exception {
        // PRIVATE product 文件，没有 menu:product → 403
        User user = new User("admin", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("btn:order:view"))));

        fileService.nextActiveFile = fileStorage(304L, "PRIVATE", "image/png");
        fileService.nextBindings = List.of(binding(304L, "product"));

        mockMvc.perform(get("/api/files/304/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void previewPrivateInventoryLogFileWithViewAllSucceedsWithoutViewLog() throws Exception {
        // PRIVATE inventory_log 文件 + btn:file:viewAll（无 btn:inventory:viewLog）→ 成功
        User user = new User("admin", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("btn:file:viewAll"))));

        fileService.nextActiveFile = fileStorage(305L, "PRIVATE", "image/png");
        fileService.nextBindings = List.of(binding(305L, "inventory_log"));
        fileService.nextResource = new ByteArrayResource(new byte[]{10, 11, 12});

        mockMvc.perform(get("/api/files/305/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void previewPrivateUnboundFileWithViewOwnAndReliableUserIdSucceeds() throws Exception {
        // PRIVATE unbound 文件 + btn:file:viewOwn + 可靠的匹配 userId → 成功
        com.blade.system.user.entity.User customUser = new com.blade.system.user.entity.User();
        customUser.setId(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customUser, null,
                        List.of(new SimpleGrantedAuthority("btn:file:viewOwn"))));

        FileStorage fs = fileStorage(306L, "PRIVATE", "image/png");
        fs.setCreateBy(42L);
        fileService.nextActiveFile = fs;
        fileService.nextBindings = List.of(); // 无绑定
        fileService.nextResource = new ByteArrayResource(new byte[]{13, 14, 15});

        mockMvc.perform(get("/api/files/306/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void previewPrivateTempBindingWithViewOwnAndReliableUserIdSucceeds() throws Exception {
        com.blade.system.user.entity.User customUser = new com.blade.system.user.entity.User();
        customUser.setId(43L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customUser, null,
                        List.of(new SimpleGrantedAuthority("btn:file:viewOwn"))));

        FileStorage fs = fileStorage(309L, "PRIVATE", "image/png");
        fs.setCreateBy(43L);
        fileService.nextActiveFile = fs;
        fileService.nextBindings = List.of(binding(309L, "temp"));
        fileService.nextResource = new ByteArrayResource(new byte[]{19, 20, 21});

        mockMvc.perform(get("/api/files/309/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void previewPrivateUnboundFileWithViewOwnButNoReliableUserIdFails() throws Exception {
        // PRIVATE unbound 文件 + btn:file:viewOwn 但 principal 不是自定义 User → 403
        User user = new User("admin", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("btn:file:viewOwn"))));

        fileService.nextActiveFile = fileStorage(307L, "PRIVATE", "image/png");
        fileService.nextBindings = List.of(); // 无绑定

        mockMvc.perform(get("/api/files/307/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void previewPublicFileStillSucceedsAnonymouslyAfterBE1009B() throws Exception {
        // PUBLIC 文件仍然可以匿名访问
        fileService.nextActiveFile = fileStorage(308L, "PUBLIC", "image/png");
        fileService.nextResource = new ByteArrayResource(new byte[]{16, 17, 18});

        mockMvc.perform(get("/api/files/308/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));

        assertThat(fileService.capturedPreviewId).isEqualTo(308L);
    }

    // === 辅助方法 ===

    private FileStorage fileStorage(Long id, String visibility, String contentType) {
        FileStorage fs = new FileStorage();
        fs.setId(id);
        fs.setVisibility(visibility);
        fs.setContentType(contentType);
        return fs;
    }

    private FileUploadVO uploadVO(Long id, String originalName, String contentType, Long fileSize) {
        return uploadVO(id, originalName, contentType, fileSize,
                contentType != null && contentType.startsWith("video/") ? "VIDEO" : "IMAGE",
                originalName != null && originalName.contains(".")
                        ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                        : null);
    }

    private FileUploadVO uploadVO(Long id, String originalName, String contentType, Long fileSize,
                                  String fileType, String fileExt) {
        FileUploadVO vo = new FileUploadVO();
        vo.setId(id);
        vo.setOriginalName(originalName);
        vo.setContentType(contentType);
        vo.setFileSize(fileSize);
        vo.setFileType(fileType);
        vo.setFileExt(fileExt);
        vo.setUrl("/api/files/" + id + "/preview");
        return vo;
    }

    private FileBusinessBind binding(Long fileId, String businessType) {
        FileBusinessBind b = new FileBusinessBind();
        b.setFileId(fileId);
        b.setBusinessType(businessType);
        b.setDeleted(0);
        return b;
    }

    private FileVO fileVO(Long id, String originalName) {
        FileVO vo = new FileVO();
        vo.setId(id);
        vo.setOriginalName(originalName);
        vo.setContentType("image/png");
        vo.setFileSize(4096L);
        vo.setFileType("IMAGE");
        vo.setFileExt("png");
        vo.setAccessUrl("/api/files/" + id + "/preview");
        vo.setStatus(1);
        return vo;
    }

    // === Stub ===

    private static class CapturingFileService implements FileService {
        // 原有字段
        private FileUploadVO nextUpload;
        private MultipartFile capturedFile;
        private String capturedBusinessType;
        private Long capturedBusinessId;
        private Long capturedOperatorId;

        // BE-1002 新增字段
        private PageResult<FileVO> nextPageResult;
        private FilePageDTO capturedPageDTO;
        private FileVO nextDetail;
        private Long capturedDetailId;

        // BE-1009A 新增字段
        private FileStorage nextActiveFile;
        private Resource nextResource;
        private Long capturedPreviewId;

        // BE-1009B 新增字段
        private List<FileBusinessBind> nextBindings;
        private Long capturedBindingsFileId;

        @Override
        public FileUploadVO upload(MultipartFile file, String businessType, Long businessId, Long operatorId) {
            capturedFile = file;
            capturedBusinessType = businessType;
            capturedBusinessId = businessId;
            capturedOperatorId = operatorId;
            return nextUpload;
        }

        @Override
        public FileStorage getActiveFile(Long id) {
            capturedPreviewId = id;
            if (nextActiveFile == null) {
                throw new UnsupportedOperationException("nextActiveFile not configured in test");
            }
            return nextActiveFile;
        }

        @Override
        public Resource loadResource(Long id) {
            if (nextResource == null) {
                throw new UnsupportedOperationException("nextResource not configured in test");
            }
            return nextResource;
        }

        @Override
        public void delete(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void bindFiles(String businessType, Long businessId, List<Long> fileIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void bindFilesFromJson(String businessType, Long businessId, String imagesJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<FileVO> pageList(FilePageDTO dto) {
            capturedPageDTO = dto;
            return nextPageResult;
        }

        @Override
        public FileVO getDetail(Long id) {
            capturedDetailId = id;
            return nextDetail;
        }

        @Override
        public List<FileBusinessBind> getActiveBindings(Long fileId) {
            capturedBindingsFileId = fileId;
            if (nextBindings == null) {
                return List.of();
            }
            return nextBindings;
        }
    }
}
