package com.blade.common.exception;

import com.blade.common.result.R;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第四批终审补强：GlobalExceptionHandler 对包装异常的 cause-chain 语义。
 *
 * <p>真实观察：MyBatis 把 {@code TenantLineHandler} 抛出的 BusinessException 包装为
 * {@code MyBatisSystemException -> PersistenceException -> BusinessException}；
 * 处理器必须在受限深度内找回 BusinessException 的业务码，否则会误报 400。</p>
 */
class GlobalExceptionHandlerCauseChainTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrappedBusinessExceptionReturnsItsCodeAndMessage() {
        BusinessException business = BusinessException.of(403, "缺少租户上下文");
        RuntimeException wrapped = new MyBatisSystemException(new PersistenceException(business));

        R<?> result = handler.handleRuntimeException(wrapped);

        assertEquals(403, result.getCode());
        assertEquals("缺少租户上下文", result.getMessage());
    }

    @Test
    void plainRuntimeExceptionKeeps400AndOwnMessage() {
        R<?> result = handler.handleRuntimeException(new RuntimeException("普通异常"));

        assertEquals(400, result.getCode());
        assertEquals("普通异常", result.getMessage());
    }

    @Test
    void nonBusinessCauseIsNotMappedTo403NorLeaked() {
        RuntimeException wrapped = new RuntimeException("外层可展示", new IllegalStateException("内部敏感细节"));

        R<?> result = handler.handleRuntimeException(wrapped);

        assertEquals(400, result.getCode());
        assertEquals("外层可展示", result.getMessage());
        assertFalse(String.valueOf(result.getMessage()).contains("内部敏感细节"));
    }

    @Test
    void cyclicCauseChainDoesNotHangAndStays400() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b");
        a.initCause(b);
        b.initCause(a);

        R<?> result = handler.handleRuntimeException(a);

        assertEquals(400, result.getCode());
    }

    @Test
    void directBusinessExceptionHandlerUnchanged() {
        R<?> result = handler.handleBusinessException(BusinessException.of(400, "参数错误"));

        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMessage());
    }

    @Test
    void deepNonBusinessChainStillReturns400WithoutBusinessCode() {
        RuntimeException chain = new RuntimeException("level0");
        for (int i = 1; i <= 12; i++) {
            chain = new RuntimeException("level" + i, chain);
        }

        R<?> result = handler.handleRuntimeException(chain);

        assertEquals(400, result.getCode());
        assertTrue(String.valueOf(result.getMessage()).startsWith("level"));
    }
}
