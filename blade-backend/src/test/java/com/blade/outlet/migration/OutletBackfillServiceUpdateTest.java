package com.blade.outlet.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-3：UPDATE 必须带 tenant_id + source_outlet_id IS NULL + 分块 id IN。
 */
class OutletBackfillServiceUpdateTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OutletBackfillService service = new OutletBackfillService(jdbc, new ObjectMapper());

    @Test
    void chunksLargeIdListsAndEnforcesTenantAndNullPredicate() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        List<Long> ids = IntStream.range(0, 1200).mapToObj(i -> (long) i).toList();

        int updated = service.updateSourceOutletId("sale_order", ids, 7L, 1L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).update(sqlCaptor.capture(), any(Object[].class));
        assertEquals(3, updated);
        String firstSql = sqlCaptor.getAllValues().get(0);
        assertTrue(firstSql.contains("SET source_outlet_id=?"));
        assertTrue(firstSql.contains("tenant_id=?"));
        assertTrue(firstSql.contains("source_outlet_id IS NULL"));
        assertTrue(firstSql.contains("id IN ("));
    }

    @Test
    void emptyIdListDoesNotTouchDatabase() {
        assertEquals(0, service.updateSourceOutletId("order_draft", List.of(), 7L, 1L));
        verify(jdbc, times(0)).update(anyString(), any(Object[].class));
    }
}
