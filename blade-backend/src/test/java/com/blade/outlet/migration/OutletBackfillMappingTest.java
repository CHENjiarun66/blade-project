package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutletBackfillMappingTest {

    private static final String HEADER = "tenant_id,legacy_source_shop,outlet_code,decision,reason\n";

    @Test
    void parsesMapSkipReviewAndQuotedReason() {
        List<OutletBackfillMappingRow> rows = OutletBackfillMapping.parse(HEADER
                + "1,御龙,YL,MAP,人工确认\n"
                + "1,42,,SKIP,\"纯数字, 疑似批次\"\n"
                + "1,未知店,,review,需要人工判断\n");

        assertEquals(3, rows.size());
        assertEquals("MAP", rows.get(0).decision());
        assertEquals("YL", rows.get(0).outletCode());
        assertEquals("纯数字, 疑似批次", rows.get(1).reason());
        assertEquals("REVIEW", rows.get(2).decision());
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        List<OutletBackfillMappingRow> rows = OutletBackfillMapping.parse(
                "# comment\n\n" + HEADER + "1,总店,ZONG,MAP,\n");
        assertEquals(1, rows.size());
    }

    @Test
    void rejectsBadHeader() {
        assertThrows(BusinessException.class,
                () -> OutletBackfillMapping.parse("tenant_id,shop,outlet,decision,reason\n1,A,B,MAP,x\n"));
    }

    @Test
    void rejectsNumericMapValueAndMissingOutletCode() {
        assertThrows(BusinessException.class, () -> OutletBackfillMapping.parse(HEADER + "1,42,YL,MAP,x\n"));
        assertThrows(BusinessException.class, () -> OutletBackfillMapping.parse(HEADER + "1,御龙,,MAP,x\n"));
    }

    @Test
    void rejectsNonMapWithOutletCodeOrMissingReason() {
        assertThrows(BusinessException.class, () -> OutletBackfillMapping.parse(HEADER + "1,御龙,YL,SKIP,x\n"));
        assertThrows(BusinessException.class, () -> OutletBackfillMapping.parse(HEADER + "1,御龙,,SKIP,\n"));
        assertThrows(BusinessException.class, () -> OutletBackfillMapping.parse(HEADER + "1,御龙,,REVIEW,\n"));
    }

    @Test
    void rejectsDuplicateLegacyValueWithinTenant() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> OutletBackfillMapping.parse(HEADER
                        + "1,御龙,YL,MAP,\n"
                        + "1,御龙,ZONG,MAP,\n"));
        assertTrue(ex.getMessage().contains("重复"));
    }

    @Test
    void rejectsInvalidTenantAndDecision() {
        assertThrows(BusinessException.class, () -> OutletBackfillMapping.parse(HEADER + "x,御龙,YL,MAP,\n"));
        assertThrows(BusinessException.class, () -> OutletBackfillMapping.parse(HEADER + "1,御龙,YL,APPLY,\n"));
    }
}
