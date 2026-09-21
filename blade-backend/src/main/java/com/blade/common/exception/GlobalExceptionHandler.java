package com.blade.common.exception;

import com.blade.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** cause 链安全遍历上限，防止异常链异常深或成环。 */
    private static final int MAX_CAUSE_DEPTH = 8;

    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusinessException(BusinessException e,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        // Agent 入口：请求级 401/403 设置真实 HTTP 状态，与 AccessDeniedException 分支一致。
        // 其它 PC/既有路径响应体契约保持不变（HTTP 200 + body code）。
        if ((e.getCode() == 401 || e.getCode() == 403)
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith("/api/agent/")) {
            response.setStatus(e.getCode());
        }
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public R<?> handleAccessDeniedException(AccessDeniedException e,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/agent/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        return R.fail(403, "您没有该操作权限，请联系管理员授权");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        return R.fail(400, message);
    }

    @ExceptionHandler(BindException.class)
    public R<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数绑定失败";
        return R.fail(400, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return R.fail(400, "文件大小超过上传限制");
    }

    @ExceptionHandler(RuntimeException.class)
    public R<?> handleRuntimeException(RuntimeException e) {
        // MyBatis/MyBatis-Plus 会把拦截器抛出的 BusinessException 包装成 MyBatisSystemException；
        // 在受限深度的 cause 链中找回 BusinessException 的业务码，避免 403 被误报为 400。
        // 仅当确实存在 BusinessException 时才采用其 code/message；否则保持原 RuntimeException 的 400。
        BusinessException business = findBusinessException(e);
        if (business != null) {
            return R.fail(business.getCode(), business.getMessage());
        }
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(400, e.getMessage());
    }

    /** 安全遍历 cause 链（防环、有限深度）；只认 BusinessException，不回传其它 cause 文本。 */
    private static BusinessException findBusinessException(Throwable throwable) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof BusinessException business) {
                return business;
            }
            if (!seen.add(current)) {
                return null; // 检测到循环 cause，停止
            }
            current = current.getCause();
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        log.error("未处理异常: {}", e.getMessage(), e);
        return R.fail(500, "服务器内部错误，请稍后重试");
    }
}
