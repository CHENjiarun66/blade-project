package com.blade.file;

import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.dto.FileBatchDeleteDTO;
import com.blade.file.dto.FileBatchMoveDTO;
import com.blade.file.dto.FileBindingCreateDTO;
import com.blade.file.dto.FileBindingVO;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.entity.FileStorage;
import com.blade.file.policy.FileBusinessAccessPolicy;
import com.blade.file.service.FileBindingService;
import com.blade.file.service.FileService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Series E2：文件中心与 order/order_draft 图片的档口权限闭环（真实隔离库）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FileOutletAccessPolicyTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private FileBusinessAccessPolicy policy;
    @Autowired private FileService fileService;
    @Autowired private FileBindingService fileBindingService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ==================== 种子 ====================

    private void auth(long userId, String... authorities) {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(userId);
        principal.setUsername("u" + userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private long seedOutlet(long tenantId, String code) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) "
                + "VALUES(?,?,?,'STORE',1,0,0,0)", tenantId, code, "档口" + code);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=? AND outlet_code=?", Long.class, tenantId, code);
    }

    private long seedUser(long tenantId, String name) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,?,0)",
                name, "x", name, tenantId);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
    }

    private void bindUser(long userId, long outletId) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) VALUES(1,?,?,0,1,0)",
                userId, outletId);
    }

    private long seedOrder(long tenantId, Long outletId, Long salesmanId) {
        String no = "E2F-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,
                  gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,
                  write_off_amount,gross_profit,fulfillment_status,fulfillment_mode,collection_status,
                  salesman_id,source_outlet_id,tenant_id,deleted,version)
                VALUES(?,?,'SPOT','E2客户',100,0,100,0,0,0,0,0,40,'CONFIRMED','UNDECIDED','PARTIAL',?,?,?,0,0)
                """, no, LocalDate.now(), salesmanId, outletId, tenantId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, no);
    }

    private long seedDraft(long tenantId, Long outletId, Long createdByUserId) {
        String ref = "E2D-" + System.nanoTime();
        jdbc.update("INSERT INTO order_draft(tenant_id,external_ref_no,source_batch_no,source_order_no,customer_name,"
                        + "status,source_outlet_id,created_by_user_id,warning_acknowledged,deleted) "
                        + "VALUES(?,?,'E2',?,?,'EDITING',?,?,0,0)",
                tenantId, ref, ref, "E2草稿客户", outletId, createdByUserId);
        return jdbc.queryForObject("SELECT id FROM order_draft WHERE external_ref_no=?", Long.class, ref);
    }

    private long seedFile(long tenantId, Long createBy, String visibility, String businessType, Long businessId) {
        return seedFile(tenantId, createBy, visibility, businessType, businessId,
                "e2-" + System.nanoTime() + ".png");
    }

    private long seedFile(long tenantId, Long createBy, String visibility, String businessType, Long businessId,
                          String originalName) {
        String key = "e2/" + System.nanoTime() + ".png";
        jdbc.update("INSERT INTO file_storage(file_key,original_name,file_name,storage_type,storage_path,status,tenant_id,"
                        + "create_by,visibility,business_type,business_id,file_type) "
                        + "VALUES(?,?,?,'local',?,1,?,?,?,?,?,'IMAGE')",
                key, originalName, originalName, key, tenantId, createBy, visibility, businessType, businessId);
        return jdbc.queryForObject("SELECT id FROM file_storage WHERE file_key=?", Long.class, key);
    }

    private long bind(long fileId, String type, long businessId) {
        return bindTenant(fileId, type, businessId, 1L);
    }

    private long bindTenant(long fileId, String type, long businessId, long tenantId) {
        jdbc.update("INSERT INTO file_business_bind(file_id,business_type,business_id,sort,is_primary,tenant_id,deleted) "
                + "VALUES(?,?,?,0,1,?,0)", fileId, type, businessId, tenantId);
        return jdbc.queryForObject("SELECT id FROM file_business_bind WHERE file_id=? AND business_type=? AND business_id=?",
                Long.class, fileId, type, businessId);
    }

    private FileStorage fileById(long id) {
        return fileService.getActiveFile(id);
    }

    private long countBind(long fileId, String type, long businessId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM file_business_bind WHERE file_id=? AND business_type=? AND business_id=? AND deleted=0",
                Long.class, fileId, type, businessId);
    }

    // ==================== 读：A 成功 / B 拒绝 ====================

    @Test
    void salespersonCanReadOwnOutletOrderFile_butNotOtherOutlet() {
        long outletA = seedOutlet(1L, "E2-A");
        long outletB = seedOutlet(1L, "E2-B");
        long salesA = seedUser(1L, "e2sa" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2sb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderA = seedOrder(1L, outletA, salesA);
        long orderB = seedOrder(1L, outletB, salesB);
        long fileA = seedFile(1L, salesA, "PRIVATE", null, null);
        long fileB = seedFile(1L, salesB, "PRIVATE", null, null);
        bind(fileA, "order", orderA);
        bind(fileB, "order", orderB);

        auth(salesA, "btn:order:view");
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(fileA)));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(fileB))).getCode(), "A 销售员不得读 B 档口订单图片");
    }

    @Test
    void multiBindingAandB_deniedForA() {
        long outletA = seedOutlet(1L, "E2-M-A");
        long outletB = seedOutlet(1L, "E2-M-B");
        long salesA = seedUser(1L, "e2ma" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2mb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderA = seedOrder(1L, outletA, salesA);
        long orderB = seedOrder(1L, outletB, salesB);
        long file = seedFile(1L, salesA, "PRIVATE", null, null);
        bind(file, "order", orderA);
        bind(file, "order", orderB);

        auth(salesA, "btn:order:view");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode(),
                "多敏感绑定必须全部可访问，任一不可访问即拒绝");
    }

    @Test
    void publicOtherOutletOrderFile_stillDenied() {
        long outletA = seedOutlet(1L, "E2-P-A");
        long outletB = seedOutlet(1L, "E2-P-B");
        long salesA = seedUser(1L, "e2pa" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2pb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderB = seedOrder(1L, outletB, salesB);
        long file = seedFile(1L, salesB, "PUBLIC", null, null);
        bind(file, "order", orderB);

        auth(salesA, "btn:order:view");
        assertTrue(policy.hasSensitiveTargets(fileById(file)), "PUBLIC 订单图片仍属敏感");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode(),
                "PUBLIC 不能把订单图片变成跨档口公开");
    }

    @Test
    void legacyOnlyOrderBinding_isProtected() {
        long outletA = seedOutlet(1L, "E2-L-A");
        long outletB = seedOutlet(1L, "E2-L-B");
        long salesA = seedUser(1L, "e2la" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2lb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        bindUser(salesB, outletB);
        long orderB = seedOrder(1L, outletB, salesB);
        long file = seedFile(1L, salesB, "PRIVATE", "order", orderB); // 仅 legacy，无权威绑定

        auth(salesA, "btn:order:view");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode(),
                "权威绑定为空时 legacy order 字段仍受保护");

        auth(salesB, "btn:order:view");
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(file)));
    }

    @Test
    void legacyOnlyDraftBinding_isProtected() {
        long outletA = seedOutlet(1L, "E2-LD-A");
        long outletB = seedOutlet(1L, "E2-LD-B");
        long salesA = seedUser(1L, "e2lda" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2ldb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long draftB = seedDraft(1L, outletB, salesB);
        long file = seedFile(1L, salesB, "PRIVATE", "order_draft", draftB);

        auth(salesA, "btn:order:view");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode());
    }

    @Test
    void unboundTempFile_ownerOtherViewAll() {
        long outletA = seedOutlet(1L, "E2-U-A");
        long salesA = seedUser(1L, "e2ua" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2ub" + System.nanoTime() % 100000);
        long admin = seedUser(1L, "e2uc" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long file = seedFile(1L, salesA, "PRIVATE", null, null);

        auth(salesA, "btn:file:viewOwn");
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(file)), "创建者本人可读临时文件");

        auth(salesB, "btn:file:viewOwn");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode(), "其他销售员不可读");

        auth(admin, "btn:file:viewAll");
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(file)), "viewAll 可读临时文件");
    }

    // ==================== list 预分页 ====================

    @Test
    void fileCenterList_excludesOtherOutletFiles_andCountMatches() {
        long outletA = seedOutlet(1L, "E2-LST-A");
        long outletB = seedOutlet(1L, "E2-LST-B");
        long salesA = seedUser(1L, "e2lsta" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2lstb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderA = seedOrder(1L, outletA, salesA);
        long orderB = seedOrder(1L, outletB, salesB);
        String prefix = "e2list-" + System.nanoTime();
        long fileA = seedFile(1L, salesA, "PRIVATE", null, null, prefix + "-A.png");
        long fileB = seedFile(1L, salesB, "PRIVATE", null, null, prefix + "-B.png");
        bind(fileA, "order", orderA);
        bind(fileB, "order", orderB);

        auth(salesA, "btn:order:view", "menu:file");
        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(50L);
        dto.setKeyword(prefix);
        PageResult<?> page = fileService.pageList(dto);
        List<Long> ids = page.getRecords().stream()
                .map(record -> (Long) ((com.blade.file.dto.FileVO) record).getId())
                .toList();
        assertTrue(ids.contains(fileA));
        assertFalse(ids.contains(fileB), "列表不得包含 B 档口订单图片");
        assertEquals(1L, page.getTotal(), "count 与 page 使用同一可见性 SQL，B 不计入总数");

        // 订单图片入口同样不得泄露 B
        dto.setBusinessType("order");
        PageResult<?> orderPage = fileService.pageList(dto);
        List<Long> orderIds = orderPage.getRecords().stream()
                .map(record -> (Long) ((com.blade.file.dto.FileVO) record).getId())
                .toList();
        assertTrue(orderIds.contains(fileA));
        assertFalse(orderIds.contains(fileB));
    }

    @Test
    void fileCenterList_unboundOnlyOwnerOrViewAll() {
        long outletA = seedOutlet(1L, "E2-UB-A");
        long salesA = seedUser(1L, "e2uba" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2ubb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        String prefix = "e2ub-" + System.nanoTime();
        long file = seedFile(1L, salesA, "PRIVATE", null, null, prefix + ".png");

        auth(salesA, "menu:file");
        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(50L);
        dto.setBound(false);
        dto.setKeyword(prefix);
        assertTrue(fileService.pageList(dto).getRecords().stream()
                .anyMatch(r -> file == ((com.blade.file.dto.FileVO) r).getId()));

        auth(salesB, "menu:file");
        assertFalse(fileService.pageList(dto).getRecords().stream()
                .anyMatch(r -> file == ((com.blade.file.dto.FileVO) r).getId()), "未绑定文件不应对他人可见");
    }

    @Test
    void peopleAll_keepsOwnUnboundFileVisible_butNotOthers_andOrderDraftScopeStillApplies() {
        long outletA = seedOutlet(1L, "E2-PA-A");
        long outletB = seedOutlet(1L, "E2-PB-B");
        long salesA = seedUser(1L, "e2paa" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2pab" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        bindUser(salesB, outletB);

        String prefix = "e2pa-" + System.nanoTime();
        long ownUnbound = seedFile(1L, salesA, "PRIVATE", null, null, prefix + "-own.png");
        long otherUnbound = seedFile(1L, salesB, "PRIVATE", null, null, prefix + "-other.png");
        long orderB = seedOrder(1L, outletB, salesB);
        long orderFileB = seedFile(1L, salesB, "PRIVATE", null, null, prefix + "-order.png");
        bind(orderFileB, "order", orderB);
        long draftB = seedDraft(1L, outletB, salesB);
        long draftFileB = seedFile(1L, salesB, "PRIVATE", null, null, prefix + "-draft.png");
        bind(draftFileB, "order_draft", draftB);

        // A 有 data:order:peopleAll 但没有 btn:file:viewAll
        auth(salesA, "menu:file", "data:order:peopleAll");
        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(50L);
        dto.setKeyword(prefix);
        PageResult<?> page = fileService.pageList(dto);
        List<Long> ids = page.getRecords().stream()
                .map(r -> (Long) ((com.blade.file.dto.FileVO) r).getId())
                .toList();

        assertTrue(ids.contains(ownUnbound), "peopleAll 下本人未绑定文件必须在列表可见");
        assertFalse(ids.contains(otherUnbound), "他人未绑定文件不可见");
        assertFalse(ids.contains(orderFileB), "peopleAll 只放开人员维度，不得绕过 B 档口订单绑定");
        assertFalse(ids.contains(draftFileB), "peopleAll 只放开人员维度，不得绕过 B 档口草稿绑定");
        assertEquals(1L, page.getTotal(), "count 与列表使用同一可见性 SQL，必须一致");

        assertDoesNotThrow(() -> policy.requireFileRead(fileById(ownUnbound)), "详情可读本人未绑定文件");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(otherUnbound))).getCode(), "详情与列表一致：他人未绑定 403");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(orderFileB))).getCode(), "订单绑定仍按档口范围约束");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(draftFileB))).getCode(), "草稿绑定仍按档口范围约束");
    }

    // ==================== 变更：伪造目标 / 不可访问文件 ====================

    @Test
    void forgedBindToOtherOutletOrder_rejectedWithoutSideEffect() {
        long outletA = seedOutlet(1L, "E2-BD-A");
        long outletB = seedOutlet(1L, "E2-BD-B");
        long salesA = seedUser(1L, "e2bda" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2bdb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderB = seedOrder(1L, outletB, salesB);
        long temp = seedFile(1L, salesA, "PRIVATE", null, null);

        auth(salesA, "btn:order:view", "btn:file:viewOwn");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileService.bindFiles("order", orderB, List.of(temp))).getCode());
        assertEquals(0, countBind(temp, "order", orderB), "拒绝后不得产生绑定");

        FileBindingCreateDTO dto = new FileBindingCreateDTO();
        dto.setFileIds(List.of(temp));
        dto.setBusinessType("order");
        dto.setBusinessId(orderB);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileBindingService.createBindings(dto)).getCode());
        assertEquals(0, countBind(temp, "order", orderB));
    }

    @Test
    void uploadWithOtherOutletBusinessId_rejectedBeforeStore() {
        long outletA = seedOutlet(1L, "E2-UP-A");
        long outletB = seedOutlet(1L, "E2-UP-B");
        long salesA = seedUser(1L, "e2upa" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2upb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderB = seedOrder(1L, outletB, salesB);
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM file_storage WHERE tenant_id=1", Long.class);

        auth(salesA, "btn:order:view");
        MockMultipartFile upload = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3});
        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileService.upload(upload, "order", orderB, salesA)).getCode());
        long after = jdbc.queryForObject("SELECT COUNT(*) FROM file_storage WHERE tenant_id=1", Long.class);
        assertEquals(before, after, "目标授权失败时不得写入文件");
    }

    @Test
    void deleteUnbindBatchMove_cannotTouchInaccessibleFile() {
        long outletA = seedOutlet(1L, "E2-DL-A");
        long outletB = seedOutlet(1L, "E2-DL-B");
        long salesA = seedUser(1L, "e2dla" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2dlb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderB = seedOrder(1L, outletB, salesB);
        long file = seedFile(1L, salesB, "PRIVATE", null, null);
        long bindId = bind(file, "order", orderB);

        auth(salesA, "btn:order:view", "menu:file");
        assertEquals(403, assertThrows(BusinessException.class, () -> fileService.delete(file)).getCode());
        assertEquals(1, jdbc.queryForObject("SELECT status FROM file_storage WHERE id=?", Integer.class, file));

        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileBindingService.deleteBinding(bindId)).getCode());
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM file_business_bind WHERE id=?", Integer.class, bindId));

        FileBatchDeleteDTO deleteDto = new FileBatchDeleteDTO();
        deleteDto.setFileIds(List.of(file));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileBindingService.batchDelete(deleteDto)).getCode());

        FileBatchMoveDTO moveDto = new FileBatchMoveDTO();
        moveDto.setFileIds(List.of(file));
        moveDto.setFolderId(null);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileBindingService.batchMove(moveDto)).getCode());
        assertNull(jdbc.queryForObject("SELECT folder_id FROM file_storage WHERE id=?", Long.class, file));
    }

    @Test
    void getBindings_rejectedForInaccessibleFile() {
        long outletA = seedOutlet(1L, "E2-GB-A");
        long outletB = seedOutlet(1L, "E2-GB-B");
        long salesA = seedUser(1L, "e2gba" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2gbb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderA = seedOrder(1L, outletA, salesA);
        long orderB = seedOrder(1L, outletB, salesB);
        long fileA = seedFile(1L, salesA, "PRIVATE", null, null);
        long fileB = seedFile(1L, salesB, "PRIVATE", null, null);
        bind(fileA, "order", orderA);
        bind(fileB, "order", orderB);

        auth(salesA, "btn:order:view");
        List<FileBindingVO> own = fileBindingService.getBindings(fileA);
        assertEquals(1, own.size());
        assertEquals(orderA, own.get(0).getBusinessId());
        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileBindingService.getBindings(fileB)).getCode());
    }

    @Test
    void tempBoundFile_ownerOtherViewAll() {
        long outletA = seedOutlet(1L, "E2-TB-A");
        long salesA = seedUser(1L, "e2tba" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2tbb" + System.nanoTime() % 100000);
        long admin = seedUser(1L, "e2tbc" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        String prefix = "e2tb-" + System.nanoTime();
        long file = seedFile(1L, salesA, "PRIVATE", null, null, prefix + ".png");
        bind(file, "temp", 0L);

        auth(salesA, "btn:file:viewOwn");
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(file)), "temp 绑定仍归创建者");

        auth(salesB, "btn:file:viewOwn");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode());

        auth(admin, "btn:file:viewAll");
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(file)));

        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setKeyword(prefix);
        auth(salesA, "menu:file");
        assertTrue(fileService.pageList(dto).getRecords().stream()
                .anyMatch(r -> file == ((com.blade.file.dto.FileVO) r).getId()), "temp 绑定列表仅创建者可见");
        auth(salesB, "menu:file");
        assertFalse(fileService.pageList(dto).getRecords().stream()
                .anyMatch(r -> file == ((com.blade.file.dto.FileVO) r).getId()));
    }

    // ==================== Codex 终审整改：跨租户污染 / 权限对齐 / 参数化范围 ====================

    @Test
    void crossTenantBindings_doNotAffectVisibilityOrRead() {
        long outletA = seedOutlet(1L, "E2-CT-A");
        long salesA = seedUser(1L, "e2cta" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e2ctb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        String prefix = "e2ct-" + System.nanoTime();
        long mappedFile = seedFile(1L, salesA, "PRIVATE", null, null, prefix + "-p.png");
        bindTenant(mappedFile, "product", 0L, 2L); // 其它租户 mapped bind
        long sensitiveFile = seedFile(1L, salesA, "PRIVATE", null, null, prefix + "-o.png");
        bindTenant(sensitiveFile, "order", 0L, 2L); // 其它租户 sensitive bind

        auth(salesB, "menu:file", "menu:product");
        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setKeyword(prefix);
        List<Long> ids = fileService.pageList(dto).getRecords().stream()
                .map(r -> (Long) ((com.blade.file.dto.FileVO) r).getId())
                .toList();
        assertFalse(ids.contains(mappedFile), "其它租户 mapped bind 不得让本租户文件可见");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(mappedFile))).getCode());

        // 本文件无本租户权威绑定 → 按未绑定归创建者；其它租户 sensitive bind 不改变可见性
        auth(salesA, "menu:file");
        assertTrue(fileService.pageList(dto).getRecords().stream()
                .anyMatch(r -> sensitiveFile == ((com.blade.file.dto.FileVO) r).getId()));
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(sensitiveFile)));
    }

    @Test
    void nonSensitiveListRequiresMatchingPermission_alignedWithDirectRead() {
        long outletA = seedOutlet(1L, "E2-PM-A");
        long salesA = seedUser(1L, "e2pma" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        String prefix = "e2pm-" + System.nanoTime();
        long productFile = seedFile(1L, salesA, "PRIVATE", null, null, prefix + "-p.png");
        bind(productFile, "product", 0L);
        long skuFile = seedFile(1L, salesA, "PRIVATE", null, null, prefix + "-s.png");
        bind(skuFile, "sku", 0L);

        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setKeyword(prefix);

        auth(salesA, "menu:file");
        assertTrue(fileService.pageList(dto).getRecords().isEmpty(), "无 menu:product 时列表不得显示 product/sku 文件");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> fileService.getDetail(productFile)).getCode(), "detail 与 list 权限对齐");

        auth(salesA, "menu:file", "menu:product");
        assertEquals(2, fileService.pageList(dto).getRecords().size());
    }

    @Test
    void noneOutletScope_hidesOrderFiles_regardlessOfOrderPermission() {
        long outletA = seedOutlet(1L, "E2-NA-A");
        long owner = seedUser(1L, "e2nao" + System.nanoTime() % 100000);
        bindUser(owner, outletA);
        long orderA = seedOrder(1L, outletA, owner);
        String prefix = "e2na-" + System.nanoTime();
        long file = seedFile(1L, owner, "PRIVATE", null, null, prefix + ".png");
        bind(file, "order", orderA);

        long noneUser = seedUser(1L, "e2na" + System.nanoTime() % 100000); // 无绑定 → NONE
        auth(noneUser, "menu:file", "btn:order:view");
        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setKeyword(prefix);
        assertTrue(fileService.pageList(dto).getRecords().isEmpty(), "NONE 范围列表不得出现订单文件");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode());
    }

    @Test
    void assignedMultiOutlet_inClauseCoversAllReadable() {
        long outletA = seedOutlet(1L, "E2-MI-A");
        long outletB = seedOutlet(1L, "E2-MI-B");
        long outletC = seedOutlet(1L, "E2-MI-C");
        long salesAB = seedUser(1L, "e2miab" + System.nanoTime() % 100000);
        long salesC = seedUser(1L, "e2mic" + System.nanoTime() % 100000);
        bindUser(salesAB, outletA);
        bindUser(salesAB, outletB);
        bindUser(salesC, outletC);
        long orderA = seedOrder(1L, outletA, salesAB);
        long orderB = seedOrder(1L, outletB, salesAB);
        long orderC = seedOrder(1L, outletC, salesC);
        String prefix = "e2mi-" + System.nanoTime();
        long fileA = seedFile(1L, salesAB, "PRIVATE", null, null, prefix + "-a.png");
        long fileB = seedFile(1L, salesAB, "PRIVATE", null, null, prefix + "-b.png");
        long fileC = seedFile(1L, salesC, "PRIVATE", null, null, prefix + "-c.png");
        bind(fileA, "order", orderA);
        bind(fileB, "order", orderB);
        bind(fileC, "order", orderC);

        auth(salesAB, "menu:file", "btn:order:view");
        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setKeyword(prefix);
        List<Long> ids = fileService.pageList(dto).getRecords().stream()
                .map(r -> (Long) ((com.blade.file.dto.FileVO) r).getId())
                .toList();
        assertTrue(ids.containsAll(List.of(fileA, fileB)), "多档口 IN 参数化应覆盖全部可读档口");
        assertFalse(ids.contains(fileC));
    }

    @Test
    void unassignedOrderFile_needsUnassignedPermission() {
        long outletA = seedOutlet(1L, "E2-UA-A");
        long salesA = seedUser(1L, "e2uaa" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderNull = seedOrder(1L, null, salesA);
        String prefix = "e2ua-" + System.nanoTime();
        long file = seedFile(1L, salesA, "PRIVATE", null, null, prefix + ".png");
        bind(file, "order", orderNull);

        FilePageDTO dto = new FilePageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setKeyword(prefix);

        auth(salesA, "menu:file", "btn:order:view", "data:order:peopleAll");
        assertTrue(fileService.pageList(dto).getRecords().isEmpty(), "无 unassigned 时待归档订单文件不可见");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode());

        auth(salesA, "menu:file", "btn:order:view", "data:order:peopleAll", "data:outlet:unassigned");
        assertTrue(fileService.pageList(dto).getRecords().stream()
                .anyMatch(r -> file == ((com.blade.file.dto.FileVO) r).getId()), "unassigned 可读待归档");
        assertDoesNotThrow(() -> policy.requireFileRead(fileById(file)));
    }

    // ==================== tenant / fail closed ====================

    @Test
    void missingTenantContext_failsClosed() {
        long outletA = seedOutlet(1L, "E2-TN-A");
        long salesA = seedUser(1L, "e2tna" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderA = seedOrder(1L, outletA, salesA);
        long file = seedFile(1L, salesA, "PRIVATE", null, null);
        bind(file, "order", orderA);

        // 不设置 TenantContext，仅设置 SecurityContext
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user(salesA), "n/a", List.of(new SimpleGrantedAuthority("btn:order:view"))));
        TenantContext.clear();
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(fileById(file))).getCode(),
                "缺少租户上下文必须 fail closed");
    }

    @Test
    void crossTenantFile_notVisibleToOtherTenant() {
        long outletOther = seedOutlet(2L, "E2-T2");
        long userOther = seedUser(2L, "e2t2" + System.nanoTime() % 100000);
        long orderOther = seedOrder(2L, outletOther, userOther);
        long fileOther = seedFile(2L, userOther, "PRIVATE", null, null);
        bind(fileOther, "order", orderOther);

        long salesA = seedUser(1L, "e2t1" + System.nanoTime() % 100000);
        auth(salesA, "btn:file:viewAll");
        FileStorage otherTenantFile = new FileStorage();
        otherTenantFile.setId(fileOther);
        otherTenantFile.setTenantId(2L);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> policy.requireFileRead(otherTenantFile)).getCode(),
                "跨租户文件不可读");
    }

    private User user(long id) {
        User principal = new User();
        principal.setId(id);
        principal.setUsername("u" + id);
        return principal;
    }
}
