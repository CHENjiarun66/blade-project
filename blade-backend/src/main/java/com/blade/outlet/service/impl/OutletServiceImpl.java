package com.blade.outlet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.outlet.dto.OutletCreateDTO;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.dto.OutletPageDTO;
import com.blade.outlet.dto.OutletUpdateDTO;
import com.blade.outlet.dto.OutletVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.entity.SysUserOutlet;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.service.OutletService;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class OutletServiceImpl implements OutletService {

    private final SalesOutletMapper outletMapper;
    private final SysUserOutletMapper sysUserOutletMapper;
    private final OrderMapper orderMapper;
    private final OrderDraftMapper orderDraftMapper;
    private final UserMapper userMapper;
    private final OutletAccessPolicy outletAccessPolicy;

    public OutletServiceImpl(SalesOutletMapper outletMapper,
                             SysUserOutletMapper sysUserOutletMapper,
                             OrderMapper orderMapper,
                             OrderDraftMapper orderDraftMapper,
                             UserMapper userMapper,
                             OutletAccessPolicy outletAccessPolicy) {
        this.outletMapper = outletMapper;
        this.sysUserOutletMapper = sysUserOutletMapper;
        this.orderMapper = orderMapper;
        this.orderDraftMapper = orderDraftMapper;
        this.userMapper = userMapper;
        this.outletAccessPolicy = outletAccessPolicy;
    }

    @Override
    public PageResult<OutletVO> pageList(OutletPageDTO dto) {
        Page<SalesOutlet> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<SalesOutlet> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            String kw = dto.getKeyword().trim();
            wrapper.and(w -> w.like(SalesOutlet::getOutletCode, kw)
                    .or().like(SalesOutlet::getOutletName, kw));
        }
        if (dto.getStatus() != null) {
            wrapper.eq(SalesOutlet::getStatus, dto.getStatus());
        }
        wrapper.orderByAsc(SalesOutlet::getSort).orderByDesc(SalesOutlet::getId);

        IPage<SalesOutlet> result = outletMapper.selectPage(page, wrapper);
        OutletCounts counts = loadCounts(result.getRecords().stream().map(SalesOutlet::getId).toList());
        List<OutletVO> voList = result.getRecords().stream().map(o -> toVO(o, counts)).toList();
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public OutletVO getById(Long id) {
        SalesOutlet outlet = requiredOutlet(id);
        return toVO(outlet, loadCounts(List.of(outlet.getId())));
    }

    @Override
    @Transactional
    public Long create(OutletCreateDTO dto) {
        String code = normalizeCode(dto.getOutletCode());
        if (outletMapper.selectCount(
                new LambdaQueryWrapper<SalesOutlet>().eq(SalesOutlet::getOutletCode, code)) > 0) {
            throw BusinessException.of(400, "档口编码已存在");
        }

        SalesOutlet outlet = new SalesOutlet();
        applyFields(outlet, code, dto.getOutletName(), dto.getOutletType(), dto.getContactName(),
                dto.getPhone(), dto.getAddress(), dto.getSort(), dto.getRemark());
        outlet.setStatus(1);
        // 先以非默认写入，避免瞬时双默认触发 uk_outlet_tenant_default；需要默认时再走锁+清除+标记
        outlet.setIsTenantDefault(0);
        outlet.setTenantId(requiredTenantId());
        outlet.setCreateBy(currentUserIdOrNull());
        outlet.setDeleted(0);

        outletMapper.insert(outlet);

        if (dto.getIsTenantDefault() != null && dto.getIsTenantDefault() == 1) {
            setTenantDefault(outlet);
        }
        return outlet.getId();
    }

    @Override
    @Transactional
    public void update(OutletUpdateDTO dto) {
        SalesOutlet outlet = requiredOutlet(dto.getId());
        String code = normalizeCode(dto.getOutletCode());
        if (!outlet.getOutletCode().equals(code)) {
            throw BusinessException.of(400, "档口编码创建后不可修改");
        }

        boolean wasDefault = Integer.valueOf(1).equals(outlet.getIsTenantDefault());
        boolean wantDefault;
        if (dto.getIsTenantDefault() != null) {
            int isDefault = dto.getIsTenantDefault() == 1 ? 1 : 0;
            if (isDefault == 1 && !Integer.valueOf(1).equals(outlet.getStatus())) {
                throw BusinessException.of(400, "默认档口必须启用");
            }
            wantDefault = isDefault == 1;
        } else {
            // dto null：保留既有默认语义；但默认档口必须启用，禁用态不得保留默认
            wantDefault = wasDefault && Integer.valueOf(1).equals(outlet.getStatus());
        }

        applyFields(outlet, code, dto.getOutletName(), dto.getOutletType(), dto.getContactName(),
                dto.getPhone(), dto.getAddress(), dto.getSort(), dto.getRemark());
        // 先在租户级串行锁下再改任何本表行，避免并发线程各自持有不同行锁后互相等待清默认造成死锁；
        // 仍保证任何默认标记写入之前目标先是非默认、其它默认已清除。
        Long tenantId = null;
        if (wantDefault) {
            tenantId = requiredTenantId();
            outletMapper.lockTenantRow(tenantId);
        }
        outlet.setIsTenantDefault(0);
        outlet.setUpdateBy(currentUserIdOrNull());
        outletMapper.updateById(outlet);

        if (wantDefault) {
            applyTenantDefault(tenantId, outlet);
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BusinessException.of(400, "状态只能为 1(启用) 或 0(禁用)");
        }
        SalesOutlet outlet = requiredOutlet(id);
        int isDefault = Integer.valueOf(1).equals(outlet.getIsTenantDefault()) ? 1 : 0;
        // 默认档口必须启用：禁用默认档口时同步清除默认标记（显式 tenant_id + deleted SQL）
        if (status == 0) {
            isDefault = 0;
        }
        outlet.setStatus(status);
        outlet.setIsTenantDefault(isDefault);
        outlet.setUpdateBy(currentUserIdOrNull());
        outletMapper.updateStatusAndDefault(requiredTenantId(), id, status, isDefault, outlet.getUpdateBy());
    }

    @Override
    public OutletOptionsVO options() {
        return outletAccessPolicy.listAvailableOptions();
    }

    private void applyFields(SalesOutlet outlet, String code, String name, String type,
                             String contactName, String phone, String address, Integer sort, String remark) {
        outlet.setOutletCode(code);
        outlet.setOutletName(name == null ? "" : name.trim());
        outlet.setOutletType(type == null || type.isBlank() ? "STORE" : type.trim());
        outlet.setContactName(contactName);
        outlet.setPhone(phone);
        outlet.setAddress(address);
        outlet.setSort(sort == null ? 0 : sort);
        outlet.setRemark(remark);
    }

    /**
     * create 场景：新行插入后取租户级串行锁，再清其它默认并标记目标。
     */
    private void setTenantDefault(SalesOutlet outlet) {
        Long tenantId = requiredTenantId();
        outletMapper.lockTenantRow(tenantId);
        applyTenantDefault(tenantId, outlet);
    }

    /**
     * 租户锁已持有的前提下：清除同租户其它默认，再标记目标为默认。
     * 两条 UPDATE 均显式带 tenant_id，跨租户互不影响；唯一索引兜底任意时刻每租户最多一个默认。
     */
    private void applyTenantDefault(Long tenantId, SalesOutlet outlet) {
        outletMapper.clearOtherTenantDefaults(tenantId, outlet.getId());
        int marked = outletMapper.markTenantDefault(tenantId, outlet.getId());
        if (marked == 0) {
            throw BusinessException.of(400, "默认档口必须启用且未删除");
        }
        outlet.setIsTenantDefault(1);
    }

    private SalesOutlet requiredOutlet(Long id) {
        if (id == null) {
            throw BusinessException.of(404, "档口不存在");
        }
        SalesOutlet outlet = outletMapper.selectById(id);
        if (outlet == null) {
            throw BusinessException.of(404, "档口不存在");
        }
        return outlet;
    }

    private OutletVO toVO(SalesOutlet o, OutletCounts counts) {
        OutletVO vo = new OutletVO();
        vo.setId(o.getId());
        vo.setOutletCode(o.getOutletCode());
        vo.setOutletName(o.getOutletName());
        vo.setOutletType(o.getOutletType());
        vo.setContactName(o.getContactName());
        vo.setPhone(o.getPhone());
        vo.setAddress(o.getAddress());
        vo.setSort(o.getSort());
        vo.setIsTenantDefault(o.getIsTenantDefault());
        vo.setStatus(o.getStatus());
        vo.setRemark(o.getRemark());
        vo.setCreateTime(o.getCreateTime());
        vo.setUpdateTime(o.getUpdateTime());
        vo.setBoundUserCount(counts.boundUsers(o.getId()));
        vo.setOrderCount(counts.orders(o.getId()));
        vo.setDraftCount(counts.drafts(o.getId()));
        return vo;
    }

    /**
     * 按当前页 outletIds 一次性 GROUP BY 统计三张关联表，查询数固定为 3，不随页大小增长。
     * 缺失计数为 0；显式带 tenant_id，不只依赖拦截器。
     */
    private OutletCounts loadCounts(List<Long> outletIds) {
        if (outletIds == null || outletIds.isEmpty()) {
            return OutletCounts.empty();
        }
        Long tenantId = requiredTenantId();
        List<Map<String, Object>> boundRows = sysUserOutletMapper.selectMaps(new QueryWrapper<SysUserOutlet>()
                .select("outlet_id AS outletId", "COUNT(*) AS cnt")
                .eq("tenant_id", tenantId).eq("status", 1).eq("deleted", 0)
                .in("outlet_id", outletIds)
                .groupBy("outlet_id"));
        List<Map<String, Object>> orderRows = orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("source_outlet_id AS outletId", "COUNT(*) AS cnt")
                .eq("tenant_id", tenantId).eq("deleted", 0)
                .in("source_outlet_id", outletIds)
                .groupBy("source_outlet_id"));
        List<Map<String, Object>> draftRows = orderDraftMapper.selectMaps(new QueryWrapper<OrderDraft>()
                .select("source_outlet_id AS outletId", "COUNT(*) AS cnt")
                .eq("tenant_id", tenantId).eq("deleted", 0)
                .in("source_outlet_id", outletIds)
                .groupBy("source_outlet_id"));
        return new OutletCounts(toCountMap(boundRows), toCountMap(orderRows), toCountMap(draftRows));
    }

    private static Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = valueIgnoreCase(row, "outletId");
            if (id instanceof Number number) {
                Object cnt = valueIgnoreCase(row, "cnt");
                counts.put(number.longValue(), cnt instanceof Number c ? c.longValue() : 0L);
            }
        }
        return counts;
    }

    private static Object valueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private record OutletCounts(Map<Long, Long> boundUsers, Map<Long, Long> orders, Map<Long, Long> drafts) {
        static OutletCounts empty() {
            return new OutletCounts(Map.of(), Map.of(), Map.of());
        }

        long boundUsers(Long outletId) { return boundUsers.getOrDefault(outletId, 0L); }
        long orders(Long outletId) { return orders.getOrDefault(outletId, 0L); }
        long drafts(Long outletId) { return drafts.getOrDefault(outletId, 0L); }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw BusinessException.of(400, "档口编码不能为空");
        }
        return code.trim();
    }

    private Long requiredTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(401, "缺少租户上下文");
        }
        return tenantId;
    }

    private Long currentUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw BusinessException.of(401, "当前用户不存在");
        }
        return userId;
    }

    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        User user = userMapper.selectByUsername(auth.getName());
        return user == null ? null : user.getId();
    }
}
