package com.blade.file.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.file.dto.FileBindDTO;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.dto.FileUploadVO;
import com.blade.file.dto.FileVO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.service.FileService;
import com.blade.system.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    /**
     * BE-1009B: 业务类型到权限码的映射
     */
    private static final Map<String, String> BUSINESS_PERMISSION_MAP = Map.of(
            "product", "menu:product",
            "sku", "menu:product",
            "order", "btn:order:view",
            "inventory_log", "btn:inventory:viewLog",
            "ocr_document", "menu:file"
    );

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public R<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam("businessType") String businessType,
                                  @RequestParam(value = "businessId", required = false) Long businessId) {
        return R.ok(fileService.upload(file, businessType, businessId, getCurrentUserId()));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        FileStorage file = fileService.getActiveFile(id);

        // BE-1009A: 非公开文件需要登录校验
        if (!"PUBLIC".equals(file.getVisibility())) {
            if (!isAuthenticated()) {
                throw new AccessDeniedException("文件未公开，需要登录后访问");
            }
            // BE-1009B: 非公开文件需要业务权限校验
            checkBusinessPermission(file);
        }

        Resource resource = fileService.loadResource(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.getContentType() != null) {
            mediaType = MediaType.parseMediaType(file.getContentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return R.ok();
    }

    @PutMapping("/bind")
    public R<Void> bind(@Valid @RequestBody FileBindDTO dto) {
        fileService.bindFiles(dto.getBusinessType(), dto.getBusinessId(), dto.getFileIds());
        return R.ok();
    }

    // ==================== BE-1002: 文件中心分页/详情 ====================

    @GetMapping
    public R<PageResult<FileVO>> list(FilePageDTO dto) {
        return R.ok(fileService.pageList(dto));
    }

    @GetMapping("/{id}")
    public R<FileVO> detail(@PathVariable Long id) {
        return R.ok(fileService.getDetail(id));
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * BE-1009B: 校验非公开文件的业务权限
     */
    private void checkBusinessPermission(FileStorage file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        // viewAll 绕过所有业务权限映射
        if (hasAuthority(authorities, "btn:file:viewAll")) {
            return;
        }

        // 查询有效绑定
        List<FileBusinessBind> bindings = fileService.getActiveBindings(file.getId());

        List<String> businessTypes = bindings.stream()
                .map(FileBusinessBind::getBusinessType)
                .filter(Objects::nonNull)
                .filter(type -> !type.isBlank())
                .distinct()
                .toList();
        if (businessTypes.isEmpty() && file.getBusinessType() != null && !file.getBusinessType().isBlank()) {
            businessTypes = List.of(file.getBusinessType());
        }

        boolean hasMappedBusinessType = false;
        for (String businessType : businessTypes) {
            String requiredPermission = BUSINESS_PERMISSION_MAP.get(businessType);
            if (requiredPermission == null) {
                continue;
            }
            hasMappedBusinessType = true;
            if (hasAuthority(authorities, requiredPermission)) {
                return;
            }
        }

        if (hasMappedBusinessType) {
            throw new AccessDeniedException("无权访问该业务文件");
        }

        // unbound/temp/unknown: 只能靠 viewOwn
        if (hasAuthority(authorities, "btn:file:viewOwn")) {
            Long userId = getReliableUserId();
            if (userId != null && userId.equals(file.getCreateBy())) {
                return;
            }
        }

        throw new AccessDeniedException("无权访问该文件");
    }

    private boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String authority) {
        return authorities.stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /**
     * 获取可靠的用户ID（不 fallback 到默认值）。
     * 仅当 principal 是 com.blade.system.user.entity.User 实例时返回其 ID，
     * 否则返回 null 表示无法可靠获取。
     */
    private Long getReliableUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L;
    }
}
