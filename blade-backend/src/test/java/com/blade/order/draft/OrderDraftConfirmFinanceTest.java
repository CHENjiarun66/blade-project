package com.blade.order.draft;

import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.entity.OrderDraftItem;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.draft.service.OrderDraftService;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 草稿确认与首笔收款交接（真实隔离库）：
 * - 定金写为正式订单首笔 RECEIPT；
 * - 纸单总额覆盖订单价值，快照按统一服务重算；
 * - 重复确认幂等返回同一订单。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderDraftConfirmFinanceTest {

    @Autowired private OrderDraftMapper draftMapper;
    @Autowired private OrderDraftItemMapper draftItemMapper;
    @Autowired private OrderDraftService draftService;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderFinancialRecordMapper financialRecordMapper;
    @Autowired private ProductSkuMapper productSkuMapper;

    private void bindContext() {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(1L);
        principal.setUsername("admin");
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new TestingAuthenticationToken(principal, null, java.util.List.of())));
    }

    private Long seedSku() {
        var sku = productSkuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.blade.product.entity.ProductSku>()
                        .eq(com.blade.product.entity.ProductSku::getTenantId, 1L)
                        .last("LIMIT 1"))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("种子库缺少 SKU"));
        return sku.getId();
    }

    private Long seedDraft(String ref, BigDecimal deposit, BigDecimal paperTotal) {
        OrderDraft draft = new OrderDraft();
        draft.setTenantId(1L);
        draft.setExternalRefNo(ref + System.currentTimeMillis());
        draft.setStatus("EDITING");
        draft.setCustomerName("草稿确认测试客户");
        draft.setDeposit(deposit);
        draft.setPaperTotalAmount(paperTotal);
        draft.setWarningAcknowledged(0);
        draft.setDeleted(0);
        draftMapper.insert(draft);

        OrderDraftItem item = new OrderDraftItem();
        item.setTenantId(1L);
        item.setDraftId(draft.getId());
        item.setSourceRowNo(1);
        item.setSkuId(seedSku());
        item.setQuantity(2);
        item.setSalePrice(new BigDecimal("50.00"));
        item.setPaperAmount(new BigDecimal("100.00"));
        item.setMatchStatus("MATCHED");
        item.setDeleted(0);
        draftItemMapper.insert(item);
        return draft.getId();
    }

    @Test
    void confirmDraft_writesDepositAsFirstReceipt_andPaperTotalWins() {
        bindContext();
        try {
            Long draftId = seedDraft("SOWB-DRAFT", new BigDecimal("20.00"), new BigDecimal("150.00"));

            OrderDraftDTO.ConfirmRequest request = new OrderDraftDTO.ConfirmRequest();
            request.setAcknowledgeWarnings(true);
            OrderDraftDTO.ConfirmResponse response = draftService.confirm(draftId, request);

            assertNotNull(response.getOrderId());
            assertFalse(response.isAlreadyConfirmed());

            Order order = orderMapper.selectById(response.getOrderId());
            assertEquals(0, order.getTotalAmount().compareTo(new BigDecimal("150.00")),
                    "纸单总额必须覆盖订单价值");
            assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("20.00")));
            assertEquals(CollectionStatus.PARTIAL.name(), order.getCollectionStatus());
            assertEquals(FulfillmentStatus.CONFIRMED.name(), order.getFulfillmentStatus());
            assertEquals(FulfillmentMode.UNDECIDED.name(), order.getFulfillmentMode());

            List<OrderFinancialRecord> receipts = financialRecordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderFinancialRecord>()
                            .eq(OrderFinancialRecord::getOrderId, order.getId())
                            .eq(OrderFinancialRecord::getRecordType, FinancialRecordType.RECEIPT.name()));
            assertEquals(1, receipts.size(), "草稿定金必须成为首笔且唯一一笔收款流水");
            assertEquals(0, receipts.get(0).getAmount().compareTo(new BigDecimal("20.00")));

            // 重复确认幂等：返回同一订单
            OrderDraftDTO.ConfirmResponse replay = draftService.confirm(draftId, request);
            assertTrue(replay.isAlreadyConfirmed());
            assertEquals(response.getOrderId(), replay.getOrderId());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void confirmDraft_withZeroDeposit_startsUnpaid() {
        bindContext();
        try {
            Long draftId = seedDraft("SOWB-DRAFT-ZERO", BigDecimal.ZERO, new BigDecimal("100.00"));

            OrderDraftDTO.ConfirmRequest request = new OrderDraftDTO.ConfirmRequest();
            request.setAcknowledgeWarnings(true);
            OrderDraftDTO.ConfirmResponse response = draftService.confirm(draftId, request);

            Order order = orderMapper.selectById(response.getOrderId());
            assertEquals(CollectionStatus.UNPAID.name(), order.getCollectionStatus());
            assertEquals(0, order.getBalanceAmount().compareTo(new BigDecimal("100.00")));
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
