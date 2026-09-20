package com.blade.outlet;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.entity.Order;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.entity.OrderOutletChangeLog;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.entity.SysUserOutlet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: 验证 DB-OUTLET-001/002/003 的实体、@TableName 和字段映射，
 * 以及 Order / OrderDraft 新增的 sourceOutletId 字段。
 */
class OutletEntityMappingTest {

    @Test
    void salesOutlet_existsWithTableNameAndFields() {
        assertTableName(SalesOutlet.class, "sales_outlet");
        assertHasField(SalesOutlet.class, "tenantId", Long.class);
        assertHasField(SalesOutlet.class, "outletCode", String.class);
        assertHasField(SalesOutlet.class, "outletName", String.class);
        assertHasField(SalesOutlet.class, "outletType", String.class);
        assertHasField(SalesOutlet.class, "isTenantDefault", Integer.class);
        assertHasField(SalesOutlet.class, "status", Integer.class);
        assertHasField(SalesOutlet.class, "deleted", Integer.class);
        assertHasField(SalesOutlet.class, "createBy", Long.class);
        assertHasField(SalesOutlet.class, "updateBy", Long.class);
    }

    @Test
    void sysUserOutlet_existsWithTableNameAndFields() {
        assertTableName(SysUserOutlet.class, "sys_user_outlet");
        assertHasField(SysUserOutlet.class, "tenantId", Long.class);
        assertHasField(SysUserOutlet.class, "userId", Long.class);
        assertHasField(SysUserOutlet.class, "outletId", Long.class);
        assertHasField(SysUserOutlet.class, "isDefault", Integer.class);
        assertHasField(SysUserOutlet.class, "status", Integer.class);
        assertHasField(SysUserOutlet.class, "deleted", Integer.class);
    }

    @Test
    void agentKeyOutlet_existsWithTableNameAndFields() {
        assertTableName(AgentKeyOutlet.class, "agent_key_outlet");
        assertHasField(AgentKeyOutlet.class, "tenantId", Long.class);
        assertHasField(AgentKeyOutlet.class, "agentKeyId", Long.class);
        assertHasField(AgentKeyOutlet.class, "outletId", Long.class);
        assertHasField(AgentKeyOutlet.class, "isDefault", Integer.class);
        assertHasField(AgentKeyOutlet.class, "status", Integer.class);
    }

    @Test
    void orderOutletChangeLog_existsWithTableNameAndFields() {
        assertTableName(OrderOutletChangeLog.class, "order_outlet_change_log");
        assertHasField(OrderOutletChangeLog.class, "tenantId", Long.class);
        assertHasField(OrderOutletChangeLog.class, "orderId", Long.class);
        assertHasField(OrderOutletChangeLog.class, "oldOutletId", Long.class);
        assertHasField(OrderOutletChangeLog.class, "oldOutletName", String.class);
        assertHasField(OrderOutletChangeLog.class, "newOutletId", Long.class);
        assertHasField(OrderOutletChangeLog.class, "newOutletName", String.class);
        assertHasField(OrderOutletChangeLog.class, "reason", String.class);
        assertHasField(OrderOutletChangeLog.class, "operatorId", Long.class);
    }

    @Test
    void orderAndDraftGainSourceOutletId() {
        assertHasField(Order.class, "sourceOutletId", Long.class);
        assertHasField(OrderDraft.class, "sourceOutletId", Long.class);
    }

    // ==================== 辅助方法 ====================

    private void assertHasField(Class<?> clazz, String fieldName, Class<?> expectedType) {
        Field field = assertDoesNotThrow(() -> clazz.getDeclaredField(fieldName),
                () -> "Field '" + fieldName + "' should exist in " + clazz.getSimpleName());
        assertEquals(expectedType, field.getType(),
                "Field '" + fieldName + "' in " + clazz.getSimpleName()
                        + " should be of type " + expectedType.getSimpleName());
    }

    private void assertTableName(Class<?> entityClass, String expectedTable) {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        assertNotNull(annotation,
                entityClass.getSimpleName() + " should be annotated with @TableName");
        assertEquals(expectedTable, annotation.value(),
                entityClass.getSimpleName() + " @TableName should be '" + expectedTable + "'");
    }
}
