package com.blade.outlet;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.outlet.dto.OutletPageDTO;
import com.blade.outlet.dto.OutletVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.service.OutletService;
import com.blade.outlet.service.impl.OutletServiceImpl;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第二批B：OutletServiceImpl.pageList 不再逐行 selectCount（N+1），改为按页内 outletIds
 * 一次性 GROUP BY 三张关联表。用 mock 调用次数证明每张表最多一次批量查询。
 */
class OutletPageCountBatchTest {

    private final SalesOutletMapper outletMapper = mock(SalesOutletMapper.class);
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderDraftMapper orderDraftMapper = mock(OrderDraftMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OutletAccessPolicy outletAccessPolicy = mock(OutletAccessPolicy.class);
    private final OutletService service = new OutletServiceImpl(
            outletMapper, sysUserOutletMapper, orderMapper, orderDraftMapper, userMapper, outletAccessPolicy);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void pageListUsesOneGroupByQueryPerRelationAndCountsCorrectly() {
        SalesOutlet a = outlet(1L, "A");
        SalesOutlet b = outlet(2L, "B");
        SalesOutlet c = outlet(3L, "C");
        Page<SalesOutlet> page = new Page<>(1L, 20L);
        page.setRecords(List.of(a, b, c));
        page.setTotal(3L);
        doReturn(page).when(outletMapper).selectPage(ArgumentMatchers.<Page<SalesOutlet>>any(), any());

        when(sysUserOutletMapper.selectMaps(any())).thenReturn(List.of(row(1L, 2L), row(2L, 3L)));
        when(orderMapper.selectMaps(any())).thenReturn(List.of(row(1L, 5L)));
        when(orderDraftMapper.selectMaps(any())).thenReturn(List.of(row(3L, 4L)));

        List<OutletVO> records = service.pageList(new OutletPageDTO()).getRecords();

        assertEquals(3, records.size());
        assertCounts(records.get(0), 2L, 5L, 0L);
        assertCounts(records.get(1), 3L, 0L, 0L);
        assertCounts(records.get(2), 0L, 0L, 4L);

        // 每张关联表最多一次批量查询；不存在逐行 selectCount
        verify(sysUserOutletMapper, times(1)).selectMaps(any());
        verify(orderMapper, times(1)).selectMaps(any());
        verify(orderDraftMapper, times(1)).selectMaps(any());
        verify(sysUserOutletMapper, never()).selectCount(any());
        verify(orderMapper, never()).selectCount(any());
        verify(orderDraftMapper, never()).selectCount(any());
    }

    @Test
    void emptyPageDoesNotQueryRelations() {
        Page<SalesOutlet> page = new Page<>(1L, 20L);
        page.setRecords(List.of());
        page.setTotal(0L);
        doReturn(page).when(outletMapper).selectPage(ArgumentMatchers.<Page<SalesOutlet>>any(), any());

        assertEquals(0, service.pageList(new OutletPageDTO()).getRecords().size());

        verify(sysUserOutletMapper, never()).selectMaps(any());
        verify(orderMapper, never()).selectMaps(any());
        verify(orderDraftMapper, never()).selectMaps(any());
    }

    private void assertCounts(OutletVO vo, long bound, long orders, long drafts) {
        assertEquals(bound, vo.getBoundUserCount(), "boundUsers for " + vo.getId());
        assertEquals(orders, vo.getOrderCount(), "orders for " + vo.getId());
        assertEquals(drafts, vo.getDraftCount(), "drafts for " + vo.getId());
    }

    private SalesOutlet outlet(long id, String code) {
        SalesOutlet o = new SalesOutlet();
        o.setId(id);
        o.setOutletCode(code);
        o.setOutletName("档口" + code);
        o.setStatus(1);
        return o;
    }

    private Map<String, Object> row(long outletId, long count) {
        return Map.of("outletId", outletId, "cnt", count);
    }
}
