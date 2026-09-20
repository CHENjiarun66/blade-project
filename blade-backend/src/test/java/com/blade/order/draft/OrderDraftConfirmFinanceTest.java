package com.blade.order.draft;

import com.blade.common.tenant.TenantContext;
import com.blade.common.result.PageResult;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileService;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.entity.OrderDraftItem;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.draft.service.OrderDraftService;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.entity.OrderItem;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductSku;
import com.blade.system.user.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 草稿确认与首笔收款交接（真实隔离库）：
 * - 定金写为正式订单首笔 RECEIPT；
 * - 纸单总额覆盖订单价值，快照按统一服务重算；
 * - 重复确认幂等返回同一订单。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderDraftConfirmFinanceTest {

    @Autowired private OrderDraftMapper draftMapper;
    @Autowired private com.blade.outlet.mapper.SalesOutletMapper salesOutletMapper;
    @Autowired private OrderDraftItemMapper draftItemMapper;
    @Autowired private OrderDraftService draftService;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private OrderFinancialRecordMapper financialRecordMapper;
    @Autowired private ProductSkuMapper productSkuMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private FileStorageMapper fileStorageMapper;
    @Autowired private FileService fileService;
    @Autowired private ObjectMapper objectMapper;

    private void bindContext() {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(1L);
        principal.setUsername("admin");
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new TestingAuthenticationToken(principal, null, java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("data:outlet:all"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("data:order:peopleAll"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("data:outlet:unassigned")))));
    }

    private Long seedSku() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Product product = new Product();
        product.setProductCode("IT-" + suffix);
        product.setName("草稿确认集成测试商品");
        product.setCategoryId(1L);
        product.setUnit("件");
        product.setStatus(1);
        product.setTenantId(1L);
        product.setDeleted(0);
        productMapper.insert(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setColorId(1L);
        sku.setSizeId(1L);
        sku.setSkuCode(product.getProductCode() + "-BLACK-XS");
        sku.setSkuType("NORMAL");
        sku.setPrice(new BigDecimal("50.00"));
        sku.setCostPrice(BigDecimal.ZERO);
        sku.setStatus(1);
        sku.setTenantId(1L);
        sku.setDeleted(0);
        productSkuMapper.insert(sku);
        return sku.getId();
    }

    private Long seededOutletId;

    private Long seedOutlet() {
        if (seededOutletId != null) return seededOutletId;
        com.blade.outlet.entity.SalesOutlet outlet = new com.blade.outlet.entity.SalesOutlet();
        outlet.setTenantId(1L);
        outlet.setOutletCode("IT-OUT-" + UUID.randomUUID().toString().substring(0, 6));
        outlet.setOutletName("集成测试档口");
        outlet.setOutletType("STORE");
        outlet.setStatus(1);
        outlet.setIsTenantDefault(1);
        outlet.setSort(0);
        outlet.setDeleted(0);
        salesOutletMapper.insert(outlet);
        seededOutletId = outlet.getId();
        return seededOutletId;
    }

    private Long seedDraft(String ref, BigDecimal deposit, BigDecimal paperTotal) {
        OrderDraft draft = new OrderDraft();
        draft.setTenantId(1L);
        draft.setSourceOutletId(seedOutlet());
        draft.setCreatedByUserId(1L);
        draft.setExternalRefNo(ref + System.currentTimeMillis());
        draft.setSourceBatchNo("TEST");
        draft.setSourceOrderNo(ref);
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

    private Long seedSourceImage(String name) {
        FileStorage file = new FileStorage();
        file.setFileKey("draft-test/" + UUID.randomUUID());
        file.setOriginalName(name);
        file.setFileName(UUID.randomUUID() + ".jpg");
        file.setContentType("image/jpeg");
        file.setFileSize(100L);
        file.setStorageType("local");
        file.setStoragePath("draft-test/" + UUID.randomUUID() + ".jpg");
        file.setFileType("IMAGE");
        file.setStatus(1);
        file.setTenantId(1L);
        fileStorageMapper.insert(file);
        return file.getId();
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
    void draftList_filtersEditingDraftsByBatch_andBuildsBatchOptions() {
        bindContext();
        try {
            Long batch39First = seedDraft("LIST-39-A", BigDecimal.ZERO, new BigDecimal("100.00"));
            Long batch39Second = seedDraft("LIST-39-B", BigDecimal.ZERO, new BigDecimal("100.00"));
            Long batch40 = seedDraft("LIST-40", BigDecimal.ZERO, new BigDecimal("100.00"));
            draftMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderDraft>()
                    .set(OrderDraft::getSourceBatchNo, "39")
                    .set(OrderDraft::getOrderDate, LocalDate.of(2026, 9, 1))
                    .in(OrderDraft::getId, List.of(batch39First, batch39Second)));
            draftMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderDraft>()
                    .set(OrderDraft::getSourceBatchNo, "40")
                    .set(OrderDraft::getOrderDate, LocalDate.of(2026, 9, 2))
                    .eq(OrderDraft::getId, batch40));
            OrderDraftItem unresolved = new OrderDraftItem();
            unresolved.setTenantId(1L);
            unresolved.setDraftId(batch40);
            unresolved.setSourceRowNo(2);
            unresolved.setMatchStatus("UNMATCHED");
            unresolved.setDeleted(0);
            draftItemMapper.insert(unresolved);

            OrderDraftDTO.ConfirmRequest confirm = new OrderDraftDTO.ConfirmRequest();
            confirm.setAcknowledgeWarnings(true);
            draftService.confirm(batch39Second, confirm);

            PageResult<OrderDraftDTO.Summary> page = draftService.page(
                    1, 20, "EDITING", null, "39", null, false, null, null);
            assertEquals(1, page.getTotal());
            assertEquals(batch39First, page.getRecords().get(0).getId());
            assertEquals("39", page.getRecords().get(0).getSourceBatchNo());

            PageResult<OrderDraftDTO.Summary> unresolvedPage = draftService.page(
                    1, 20, "EDITING", null, null, null, true, null, null);
            assertTrue(unresolvedPage.getRecords().stream().anyMatch(summary -> batch40.equals(summary.getId())));
            assertTrue(unresolvedPage.getRecords().stream().allMatch(summary -> summary.getUnresolvedCount() > 0));

            List<OrderDraftDTO.BatchSummary> batches = draftService.batches();
            OrderDraftDTO.BatchSummary batch39 = batches.stream()
                    .filter(batch -> "39".equals(batch.getSourceBatchNo()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(1, batch39.getDraftCount(), "已生成订单的草稿不能出现在待处理批次计数中");
            assertTrue(batches.stream().anyMatch(batch -> "40".equals(batch.getSourceBatchNo())));
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

    @Test
    void confirmDraft_withoutSourceShop_doesNotCopyBatchIntoSourceShop() {
        bindContext();
        try {
            Long draftId = seedDraft("SOURCE-SHOP-EMPTY", BigDecimal.ZERO, new BigDecimal("100.00"));

            OrderDraftDTO.ConfirmRequest request = new OrderDraftDTO.ConfirmRequest();
            request.setAcknowledgeWarnings(true);
            Order order = orderMapper.selectById(draftService.confirm(draftId, request).getOrderId());

            assertEquals("TEST_SOURCE-SHOP-EMPTY", order.getSourceDocNo());
            assertNull(order.getSourceShop(), "来源档口为空时不能用单据批次兜底");
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void confirmDraft_withSourceShop_preservesExplicitSourceShop() {
        bindContext();
        try {
            Long draftId = seedDraft("SOURCE-SHOP-EXPLICIT", BigDecimal.ZERO, new BigDecimal("100.00"));
            OrderDraft draft = draftMapper.selectById(draftId);
            draft.setSourceShop("御龙");
            draftMapper.updateById(draft);

            OrderDraftDTO.ConfirmRequest request = new OrderDraftDTO.ConfirmRequest();
            request.setAcknowledgeWarnings(true);
            Order order = orderMapper.selectById(draftService.confirm(draftId, request).getOrderId());

            assertEquals("御龙", order.getSourceShop());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void confirmDraft_carriesAllBoundPaperImagesIntoFormalOrder() throws Exception {
        bindContext();
        try {
            Long draftId = seedDraft("SOWB-DRAFT-IMAGES", BigDecimal.ZERO, new BigDecimal("100.00"));
            List<Long> fileIds = List.of(seedSourceImage("paper-1.jpg"), seedSourceImage("paper-2.jpg"));
            fileService.bindFiles("order_draft", draftId, fileIds);
            OrderDraft draft = draftMapper.selectById(draftId);
            draft.setSourceFileId(fileIds.get(0));
            draftMapper.updateById(draft);

            assertEquals(fileIds, draftService.get(draftId).getSourceFileIds());

            OrderDraftDTO.ConfirmRequest request = new OrderDraftDTO.ConfirmRequest();
            request.setAcknowledgeWarnings(true);
            Long orderId = draftService.confirm(draftId, request).getOrderId();
            List<String> formalOrderImages = objectMapper.readValue(
                    orderMapper.selectById(orderId).getImages(), new TypeReference<>() {});
            assertEquals(fileIds.stream().map(String::valueOf).toList(), formalOrderImages);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateDraft_reordersAndUnbindsImagesWithoutDeletingFileCenterAssets() {
        bindContext();
        try {
            Long firstFileId = seedSourceImage("paper-first.jpg");
            Long secondFileId = seedSourceImage("paper-second.jpg");
            OrderDraftDTO.Item item = new OrderDraftDTO.Item();
            item.setSkuId(seedSku());
            item.setQuantity(2);
            item.setSalePrice(new BigDecimal("50.00"));
            item.setPaperAmount(new BigDecimal("100.00"));

            OrderDraftDTO.SaveRequest request = new OrderDraftDTO.SaveRequest();
            request.setExternalRefNo("draft-image-edit-" + UUID.randomUUID());
            request.setCustomerName("图片编辑测试客户");
            request.setPaperTotalAmount(new BigDecimal("100.00"));
            request.setSourceFileIds(List.of(firstFileId, secondFileId));
            request.setItems(List.of(item));
            Long draftId = draftService.create(request).getDraftId();

            assertEquals(List.of(firstFileId, secondFileId), draftService.get(draftId).getSourceFileIds());

            request.setSourceFileIds(List.of(secondFileId, firstFileId));
            draftService.update(draftId, request);
            OrderDraftDTO.View reordered = draftService.get(draftId);
            assertEquals(secondFileId, reordered.getSourceFileId());
            assertEquals(List.of(secondFileId, firstFileId), reordered.getSourceFileIds());

            request.setSourceFileIds(List.of(secondFileId));
            draftService.update(draftId, request);
            assertEquals(List.of(secondFileId), draftService.get(draftId).getSourceFileIds());
            assertTrue(fileService.getActiveBindings(firstFileId).stream()
                    .noneMatch(bind -> "order_draft".equals(bind.getBusinessType())
                            && draftId.equals(bind.getBusinessId())));
            assertEquals(1, fileStorageMapper.selectById(firstFileId).getStatus(),
                    "从草稿移除图片不能删除文件中心原文件");

            request.setSourceFileIds(List.of());
            draftService.update(draftId, request);
            OrderDraftDTO.View withoutImages = draftService.get(draftId);
            assertNull(withoutImages.getSourceFileId());
            assertTrue(withoutImages.getSourceFileIds().isEmpty(),
                    "清空图片后不能从 file_storage 旧业务字段回退出已解绑图片");
            assertEquals(1, fileStorageMapper.selectById(secondFileId).getStatus());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createManualDraft_acceptsPartialQuickEntryWithoutCreatingFormalOrder() {
        bindContext();
        try {
            OrderDraftDTO.SaveRequest request = new OrderDraftDTO.SaveRequest();
            request.setExternalRefNo("manual-partial-" + UUID.randomUUID());
            request.setSourceOrderNo("手工单-半成品");
            request.setSourceShop("御龙");
            request.setOrderType("SPOT");
            request.setCustomerName("临时客户");
            request.setCustomerCountryCode("+86");
            request.setPaidAmount(new BigDecimal("30.00"));
            request.setFreightAmount(new BigDecimal("10.00"));
            request.setNeedDelivery(1);
            request.setDeliveryAddress("待补充详细门牌");
            request.setItems(List.of(new OrderDraftDTO.Item()));

            OrderDraftDTO.BatchResult result = draftService.create(request);
            OrderDraftDTO.View view = draftService.get(result.getDraftId());

            assertEquals("MANUAL", view.getEntrySource());
            assertEquals("手工单-半成品", view.getSourceOrderNo());
            assertEquals("御龙", view.getSourceShop());
            assertEquals("SPOT", view.getOrderType());
            assertEquals("+86", view.getCustomerCountryCode());
            assertEquals(0, view.getPaidAmount().compareTo(new BigDecimal("30.00")));
            assertEquals(1, view.getNeedDelivery());
            assertEquals(1, view.getItems().size());
            assertNull(view.getItems().get(0).getSkuId());
            assertNull(view.getConfirmedOrderId());

            OrderDraftDTO.Item completedItem = new OrderDraftDTO.Item();
            completedItem.setSkuId(seedSku());
            completedItem.setQuantity(1);
            completedItem.setSalePrice(new BigDecimal("50.00"));
            completedItem.setCostPrice(new BigDecimal("10.00"));
            completedItem.setPaperAmount(new BigDecimal("50.00"));
            request.setItems(List.of(completedItem));
            request.setWarnings(view.getWarnings());
            draftService.update(result.getDraftId(), request);

            OrderDraftDTO.View completed = draftService.get(result.getDraftId());
            assertTrue(completed.getWarnings().isEmpty(), "补齐字段后系统计算警告必须自动消失");
            assertEquals(0, completed.getItems().get(0).getCostPrice().compareTo(new BigDecimal("10.00")));
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void confirmManualDraft_preservesQuickEntryFinanceDeliveryAndCostFields() {
        bindContext();
        try {
            Long skuId = seedSku();
            OrderDraftDTO.Item item = new OrderDraftDTO.Item();
            item.setSkuId(skuId);
            item.setQuantity(2);
            item.setSalePrice(new BigDecimal("50.00"));
            item.setCostPrice(new BigDecimal("12.00"));
            item.setPaperAmount(new BigDecimal("100.00"));

            OrderDraftDTO.SaveRequest request = new OrderDraftDTO.SaveRequest();
            request.setExternalRefNo("manual-complete-" + UUID.randomUUID());
            request.setSourceBatchNo("41");
            request.setSourceOrderNo("手工单-完整");
            request.setSourceShop("御龙");
            request.setOrderType("SPOT");
            request.setCustomerName("手工草稿客户");
            request.setCustomerPhone("13800138000");
            request.setCustomerAddress("客户地址");
            request.setPaidAmount(new BigDecimal("40.00"));
            request.setFreightAmount(new BigDecimal("8.00"));
            request.setFreightCost(new BigDecimal("3.00"));
            request.setNeedDelivery(1);
            request.setDeliveryAddress("送货地址");
            request.setNote("手工暂存备注");
            request.setItems(List.of(item));
            Long draftId = draftService.create(request).getDraftId();
            // Series D 才接入档口选择器；本用例模拟已归档档口后再确认
            OrderDraft manualDraft = draftMapper.selectById(draftId);
            manualDraft.setSourceOutletId(seedOutlet());
            draftMapper.updateById(manualDraft);

            OrderDraftDTO.ConfirmRequest confirm = new OrderDraftDTO.ConfirmRequest();
            confirm.setAcknowledgeWarnings(true);
            Long orderId = draftService.confirm(draftId, confirm).getOrderId();
            Order order = orderMapper.selectById(orderId);

            assertEquals("SPOT", order.getOrderType());
            assertEquals("41_手工单-完整", order.getSourceDocNo());
            assertEquals("御龙", order.getSourceShop());
            assertEquals("客户地址", order.getCustomerAddress());
            assertEquals("送货地址", order.getDeliveryAddress());
            assertEquals(1, order.getNeedDelivery());
            assertEquals(0, order.getTotalAmount().compareTo(new BigDecimal("108.00")));
            assertEquals(0, order.getTotalCostAmount().compareTo(new BigDecimal("27.00")));
            assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("40.00")));
            assertEquals(CollectionStatus.PARTIAL.name(), order.getCollectionStatus());

            List<OrderItem> orderItems = orderItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getOrderId, orderId));
            assertEquals(1, orderItems.size());
            assertEquals(0, orderItems.get(0).getCostPrice().compareTo(new BigDecimal("12.00")));
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
