package com.blade.file.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.file.dto.FileBindDTO;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.dto.FileUploadVO;
import com.blade.file.dto.FileVO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.service.FileDerivativeService;
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
    private final FileDerivativeService derivativeService;

    /**
     * BE-1009B: 业务类型到权限码的映射
     */
    private static final Map<String, String> BUSINESS_PERMISSION_MAP = Map.of(
            "product", "menu:product",
            "sku", "menu:product",
            "order", "btn:order:view",
            "inventory_log", "btn:inventory:viewLog",
            "ocr_document", "menu:file",
            "whatsapp_message", "menu:whatsapp"
    );

    public FileController(FileService fileService, FileDerivativeService derivativeService) {
        this.fileService = fileService;
        this.derivativeService = derivativeService;
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

    /**
     * BE-1012: 获取图片派生图（thumb / card）。
     * 权限与 {@link #preview} 完全一致。
     * 派生图不存在或非 READY 时自动回落原图。
     */
    @GetMapping("/{id}/variant")
    public ResponseEntity<Resource> variant(@PathVariable Long id, @RequestParam String type) {
        if (!"thumb".equals(type) && !"card".equals(type)) {
            throw new IllegalArgumentException("不支持的派生类型: " + type + " (仅支持 thumb / card)");
        }

        FileStorage file = fileService.getActiveFile(id);

        // Same visibility/permission logic as preview()
        if (!"PUBLIC".equals(file.getVisibility())) {
            if (!isAuthenticated()) {
                throw new AccessDeniedException("文件未公开，需要登录后访问");
            }
            checkBusinessPermission(file);
        }

        // Try derivative first
        Resource resource = derivativeService.loadVariantResource(id, type);
        CacheControl cache;
        MediaType mediaType;

        if (resource != null) {
            // READY derivative — aggressive caching (immutable given unique file+type)
            cache = CacheControl.maxAge(Duration.ofDays(7));
            mediaType = MediaType.IMAGE_JPEG;
        } else {
            // Fallback to original
            resource = fileService.loadResource(id);
            cache = CacheControl.maxAge(Duration.ofHours(1));
            if (file.getContentType() != null) {
                mediaType = MediaType.parseMediaType(file.getContentType());
            } else {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(cache)
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

    // ==================== BE-1012: 历史派生图补生成 ====================

    @PostMapping("/derivatives/backfill")
    public R<FileDerivativeService.BackfillResult> backfill(
            @RequestParam(defaultValue = "100") int limit) {
        if (!isAuthenticated()) {
            throw new AccessDeniedException("需要登录后操作");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (!hasAuthority(authorities, "btn:file:viewAll")
                && !hasAuthority(authorities, "btn:file:cleanup")) {
            throw new AccessDeniedException("无文件管理权限");
        }
        return R.ok(derivativeService.backfill(limit));
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
