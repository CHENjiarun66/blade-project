package com.blade.order;

import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.service.OrderCompatAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 兼容适配器全枚举投影验证：新旧字段唯一映射点。
 */
class OrderCompatAdapterTest {

    private final OrderCompatAdapter adapter = new OrderCompatAdapter();

    @Test
    void legacyStatusProjectionCoversAllFulfillmentStates() {
        assertEquals(0, adapter.projectLegacyStatus(FulfillmentStatus.CONFIRMED));
        assertEquals(1, adapter.projectLegacyStatus(FulfillmentStatus.WAITING_ALLOCATION));
        assertEquals(2, adapter.projectLegacyStatus(FulfillmentStatus.ALLOCATING));
        assertEquals(3, adapter.projectLegacyStatus(FulfillmentStatus.READY_TO_SHIP));
        assertEquals(4, adapter.projectLegacyStatus(FulfillmentStatus.SHIPPED));
        assertEquals(5, adapter.projectLegacyStatus(FulfillmentStatus.COMPLETED));
        assertEquals(6, adapter.projectLegacyStatus(FulfillmentStatus.CANCELLED));
        assertNull(adapter.projectLegacyStatus(null));
    }

    @Test
    void legacyPaymentStatusProjectionCoversAllCollectionStates() {
        assertEquals(0, adapter.projectLegacyPaymentStatus(CollectionStatus.UNPAID));
        assertEquals(1, adapter.projectLegacyPaymentStatus(CollectionStatus.PARTIAL));
        assertEquals(2, adapter.projectLegacyPaymentStatus(CollectionStatus.SETTLED));
        assertNull(adapter.projectLegacyPaymentStatus(null));
    }

    @Test
    void displayFallbackMapsLegacyIntsForUnmigratedRows() {
        assertEquals(FulfillmentStatus.CONFIRMED, adapter.displayFulfillmentStatus(null, 0));
        assertEquals(FulfillmentStatus.READY_TO_SHIP, adapter.displayFulfillmentStatus(null, 3));
        assertEquals(FulfillmentStatus.COMPLETED, adapter.displayFulfillmentStatus(null, 5));
        assertEquals(FulfillmentStatus.CANCELLED, adapter.displayFulfillmentStatus(null, 6));
        // 旧退货语义不自动映射
        assertNull(adapter.displayFulfillmentStatus(null, 7));
        assertNull(adapter.displayFulfillmentStatus(null, 8));
        // 新字段优先
        assertEquals(FulfillmentStatus.SHIPPED, adapter.displayFulfillmentStatus("SHIPPED", 0));
    }

    @Test
    void displayFallbackMapsLegacyPaymentStatus() {
        assertEquals(CollectionStatus.UNPAID, adapter.displayCollectionStatus(null, 0));
        assertEquals(CollectionStatus.PARTIAL, adapter.displayCollectionStatus(null, 1));
        assertEquals(CollectionStatus.SETTLED, adapter.displayCollectionStatus(null, 2));
        assertNull(adapter.displayCollectionStatus(null, null));
        assertEquals(CollectionStatus.SETTLED, adapter.displayCollectionStatus("SETTLED", 0));
    }
}
