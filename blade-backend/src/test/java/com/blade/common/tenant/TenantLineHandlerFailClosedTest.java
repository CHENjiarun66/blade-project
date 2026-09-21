package com.blade.common.tenant;

import com.blade.common.exception.BusinessException;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第四批：租户上下文 fail-closed 单测。缺上下文绝不回退 tenant=1。
 */
class TenantLineHandlerFailClosedTest {

    private final TenantLineHandler handler = new TenantLineHandler();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void missingTenantDoesNotProduceTenantOne() {
        TenantContext.clear();

        BusinessException ex = assertThrows(BusinessException.class, handler::getTenantId);
        assertEquals(403, ex.getCode(), "缺上下文必须业务 403 fail closed");
        assertEquals("缺少租户上下文", ex.getMessage());

        BusinessException requireEx = assertThrows(BusinessException.class, TenantContext::requireTenantId);
        assertEquals(403, requireEx.getCode());
    }

    @Test
    void presentTenantUsesExactTenantNotOne() {
        TenantContext.setTenantId(2L);

        LongValue expression = (LongValue) handler.getTenantId();
        assertEquals(2L, expression.getValue(), "必须使用真实 tenant，不得回退 1L");
        assertEquals(2L, TenantContext.requireTenantId());
    }
}
