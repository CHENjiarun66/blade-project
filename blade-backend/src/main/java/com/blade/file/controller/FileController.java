package com.blade.file.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.file.dto.FileBindDTO;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.dto.FileUploadVO;
import com.blade.file.dto.FileVO;
import com.blade.file.entity.FileStorage;
import com.blade.file.policy.FileBusinessAccessPolicy;
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

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileDerivativeService derivativeService;
    private final FileBusinessAccessPolicy fileBusinessAccessPolicy;

    public FileController(FileService fileService,
                          FileDerivativeService derivativeService,
                          FileBusinessAccessPolicy fileBusinessAccessPolicy) {
        this.fileService = fileService;
        this.derivativeService = derivativeService;
        this.fileBusinessAccessPolicy = fileBusinessAccessPolicy;
    }

    @PostMapping("/upload")
    public R<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam("businessType") String businessType,
                                  @RequestParam(value = "businessId", required = false) Long businessId) {
        // 带业务目标的上传必须在存储文件之前完成目标授权
        if (businessId != null) {
            fileBusinessAccessPolicy.requireTargetAccess(businessType, businessId);
        }
        return R.ok(fileService.upload(file, businessType, businessId, getCurrentUserId()));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        FileStorage file = fileService.getActiveFile(id);
        authorizeMedia(file);
        return resourceResponse(file, id, CacheControl.maxAge(Duration.ofHours(1)));
    }

    /**
     * BE-1012: 获取图片派生图（thumb / card）。权限与 {@link #preview} 完全一致。
     * 派生图不存在或非 READY 时自动回落原图。
     */
    @GetMapping("/{id}/variant")
    public ResponseEntity<Resource> variant(@PathVariable Long id, @RequestParam String type) {
        if (!"thumb".equals(type) && !"card".equals(type)) {
            throw new IllegalArgumentException("不支持的派生类型: " + type + " (仅支持 thumb / card)");
        }
        FileStorage file = fileService.getActiveFile(id);
        authorizeMedia(file);

        Resource resource = derivativeService.loadVariantResource(id, type);
        CacheControl cache;
        MediaType mediaType;

        if (resource != null) {
            cache = CacheControl.maxAge(Duration.ofDays(7));
            mediaType = MediaType.IMAGE_JPEG;
        } else {
            resource = fileService.loadResource(id);
            cache = CacheControl.maxAge(Duration.ofHours(1));
            mediaType = file.getContentType() != null
                    ? MediaType.parseMediaType(file.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        }

        // 受保护订单/草稿图片不得使用 public/shared 缓存
        if (fileBusinessAccessPolicy.hasSensitiveTargets(file)) {
            cache = CacheControl.noStore();
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(cache)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        FileStorage file = fileService.getActiveFile(id);
        fileBusinessAccessPolicy.requireFileRead(file);
        fileService.delete(id);
        return R.ok();
    }

    @PutMapping("/bind")
    public R<Void> bind(@Valid @RequestBody FileBindDTO dto) {
        fileBusinessAccessPolicy.requireTargetAccess(dto.getBusinessType(), dto.getBusinessId());
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
        FileStorage file = fileService.getActiveFile(id);
        fileBusinessAccessPolicy.requireFileRead(file);
        return R.ok(fileService.getDetail(id));
    }

    /**
     * 受保护 order/order_draft 文件即使 visibility=PUBLIC 也必须按业务范围授权；
     * PUBLIC 的非敏感文件保持匿名可读；previewToken 只建立身份，不绕过业务授权。
     */
    private void authorizeMedia(FileStorage file) {
        if (fileBusinessAccessPolicy.hasSensitiveTargets(file)) {
            fileBusinessAccessPolicy.requireFileRead(file);
            return;
        }
        if (!"PUBLIC".equals(file.getVisibility())) {
            if (!isAuthenticated()) {
                throw new AccessDeniedException("文件未公开，需要登录后访问");
            }
            fileBusinessAccessPolicy.requireFileRead(file);
        }
    }

    private ResponseEntity<Resource> resourceResponse(FileStorage file, Long id, CacheControl defaultCache) {
        Resource resource = fileService.loadResource(id);
        MediaType mediaType = file.getContentType() != null
                ? MediaType.parseMediaType(file.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        CacheControl cache = fileBusinessAccessPolicy.hasSensitiveTargets(file)
                ? CacheControl.noStore()
                : defaultCache;
        return ResponseEntity.ok().contentType(mediaType).cacheControl(cache).body(resource);
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

    private boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String authority) {
        return authorities.stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L;
    }
}
